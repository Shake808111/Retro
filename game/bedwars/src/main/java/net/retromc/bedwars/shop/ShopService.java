package net.retromc.bedwars.shop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.BedWarsMode;
import net.retromc.bedwars.BwPlayer;
import net.retromc.bedwars.BwTeam;
import net.retromc.bedwars.event.ShopPurchaseEvent;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.bedwars.upgrade.UpgradeType;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.game.framework.shop.Purchase;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.platform.common.text.ColorText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

/**
 * Kauf-Ablauf (Spec 04 §6.1.6): Bestand prüfen, bei Fehlbetrag {@code bw.shop.missing} mit Restbetrag und
 * VILLAGER_NO, sonst abziehen und das Item in den bevorzugten Hotbar-Slot (Shop-Einstellungen), sonst den ersten
 * freien, sonst auf den Boden legen. Permanente Stufen ersetzen die alte (Rüstung: nur der Brustpanzer). Dazu
 * Start-/Respawn-Ausrüstung und das Anwenden der Team-Upgrades auf Items.
 */
public final class ShopService {

    private final BedWarsMode mode;
    private final Map<UUID, BedWarsStore.ShopSettings> settings = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, String>> quickshops = new ConcurrentHashMap<>();

    public ShopService(BedWarsMode mode) {
        this.mode = mode;
    }

    /** Lädt Schnellkauf und Shop-Einstellungen eines Spielers (beim Join). */
    public void load(UUID player) {
        mode.game().services().find(BedWarsStore.class).ifPresent(store -> {
            store.shopSettings(player).thenAccept(value -> settings.put(player, value)).exceptionally(error -> null);
            store.quickshop(player, group()).thenAccept(value -> quickshops.put(player, Map.copyOf(value)))
                    .exceptionally(error -> null);
        });
    }

    public String group() {
        return mode.classic() ? BedWarsStore.CLASSIC : BedWarsStore.MODERN;
    }

    public BedWarsStore.ShopSettings settings(UUID player) {
        return settings.getOrDefault(player, BedWarsStore.ShopSettings.DEFAULT);
    }

    public void settings(UUID player, BedWarsStore.ShopSettings value) {
        settings.put(player, value);
        mode.game().services().find(BedWarsStore.class).ifPresent(store -> store.saveShopSettings(player, value)
                .exceptionally(error -> {
                    mode.game().log().warn("Shop-Einstellungen von {} nicht gespeichert: {}", player, error.toString());
                    return null;
                }));
    }

    /** Schnellkauf-Belegung (gespeichert oder Standard aus {@code shop.yml}). */
    public Map<Integer, String> quickshop(UUID player) {
        Map<Integer, String> own = quickshops.get(player);
        return own == null || own.isEmpty() ? mode.catalog().defaultQuickshop() : own;
    }

    /** Setzt/leert ein Schnellkauf-Feld und speichert sofort (Spec 04 §5.5). */
    public void quickshop(UUID player, int slot, @Nullable String itemId) {
        Map<Integer, String> changed = new java.util.TreeMap<>(quickshop(player));
        if (itemId == null) {
            changed.remove(slot);
        } else {
            changed.put(slot, itemId);
        }
        quickshops.put(player, Map.copyOf(changed));
        mode.game().services().find(BedWarsStore.class).ifPresent(store -> store.saveQuickshop(player, group(),
                changed).exceptionally(error -> {
                    mode.game().log().warn("Schnellkauf von {} nicht gespeichert: {}", player, error.toString());
                    return null;
                }));
    }

    /** Erstes freies Schnellkauf-Feld oder −1. */
    public int freeQuickSlot(UUID player) {
        Map<Integer, String> slots = quickshop(player);
        for (int slot = 0; slot < BedWarsStore.QUICKSHOP_SLOTS; slot++) {
            if (!slots.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------ Kauf

    /** Kauft {@code item}; {@code true} bei Erfolg. */
    public boolean buy(Player player, ShopItem item) {
        if (!mode.playing(player) || item.free()) {
            return false;
        }
        BwPlayer bw = mode.bw(player.getUniqueId());
        if (owned(bw, item)) {
            mode.texts().send(player, "bw.shop.maxlevel");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        Material currency = ShopItems.material(item.currency().material());
        List<Integer> slots = new ArrayList<>();
        List<Integer> stacks = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == currency) {
                slots.add(slot);
                stacks.add(stack.getAmount());
            }
        }
        Purchase.Result result = Purchase.tryBuy(stacks, item.price());
        Locale locale = mode.texts().locale(player);
        if (!result.paid()) {
            mode.texts().send(player, "bw.shop.missing", Placeholder.unparsed("n", Integer.toString(result.missing())),
                    Placeholder.component("res", resourceName(locale, item.currency(), result.missing())),
                    Placeholder.component("item", mode.texts().i18n().render(locale, item.nameKey())));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        ShopPurchaseEvent event = new ShopPurchaseEvent(player, item, Map.of(item.currency(), item.price()));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            int left = result.remaining().get(i);
            ItemStack stack = inventory.getItem(slots.get(i));
            if (stack != null) {
                if (left == 0) {
                    inventory.setItem(slots.get(i), null);
                } else {
                    stack.setAmount(left);
                }
            }
        }
        deliver(player, item);
        bw.purchase();
        applyUpgrades(player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return true;
    }

    /** Ob eine permanente Stufe schon erreicht ist (höchste Stufe → {@code bw.shop.maxlevel}). */
    private static boolean owned(BwPlayer bw, ShopItem item) {
        String group = item.group();
        if (group == null) {
            return false;
        }
        return switch (group) {
            case "armor" -> bw.armor() >= item.tier();
            case "pickaxe" -> bw.pickaxe() >= item.tier();
            case "axe" -> bw.axe();
            case "shears" -> bw.shears();
            default -> false;
        };
    }

    private void deliver(Player player, ShopItem item) {
        BwPlayer bw = mode.bw(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        String group = item.group() == null ? "" : item.group();
        ItemStack stack = ShopItems.item(item, dye(player), mode.texts().i18n(), mode.texts().locale(player));
        switch (group) {
            case "armor" -> {
                bw.armor(item.tier());
                inventory.setChestplate(stack);
                return;
            }
            case "pickaxe" -> {
                bw.pickaxe(item.tier());
                int slot = removeAll(inventory, "_PICKAXE");
                place(player, stack, slot >= 0 ? slot : settings(player.getUniqueId()).slot("pickaxe"));
                return;
            }
            case "axe" -> bw.axe(true);
            case "shears" -> bw.shears(true);
            case "sword" -> {
                int slot = removeType(inventory, Material.WOODEN_SWORD);
                place(player, stack, slot >= 0 ? slot : settings(player.getUniqueId()).slot("weapon"));
                return;
            }
            default -> {
            }
        }
        place(player, stack, settings(player.getUniqueId()).slot(kind(item)));
    }

    /** Art für den bevorzugten Hotbar-Slot. */
    static String kind(ShopItem item) {
        return switch (item.category()) {
            case 1 -> "blocks";
            case 3 -> "pickaxe";
            case 4 -> item.material().equals("BOW") || item.material().equals("CROSSBOW")
                    || item.material().equals("ARROW") ? "bow" : "weapon";
            case 5 -> "potion";
            default -> "";
        };
    }

    /** Legt in den Wunsch-Slot, sonst den ersten freien, sonst auf den Boden. */
    private static void place(Player player, ItemStack stack, int preferred) {
        PlayerInventory inventory = player.getInventory();
        if (preferred >= 0 && preferred <= 8) {
            ItemStack current = inventory.getItem(preferred);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(preferred, stack);
                return;
            }
            if (current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + stack.getAmount());
                return;
            }
        }
        inventory.addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private static int removeAll(PlayerInventory inventory, String suffix) {
        int first = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType().name().endsWith(suffix)) {
                if (first < 0) {
                    first = slot;
                }
                inventory.setItem(slot, null);
            }
        }
        return first;
    }

    private static int removeType(PlayerInventory inventory, Material type) {
        int first = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == type) {
                if (first < 0) {
                    first = slot;
                }
                inventory.setItem(slot, null);
            }
        }
        return first;
    }

    // ------------------------------------------------------------------------------------------ Ausrüstung

    /**
     * Start- und Respawn-Ausrüstung (Spec 04 §4.11, §6.1.4 Nr. 3): Holzschwert und Spitzhacke der erreichten Stufe
     * an ihren gemerkten Hotbar-Slots, Axt/Schere wenn gekauft, Lederrüstung in Teamfarbe mit gekauftem Brustpanzer.
     */
    public void giveKit(Player player) {
        BwPlayer bw = mode.bw(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(bw.swordSlot(), ShopItems.unbreakable(Material.WOODEN_SWORD));
        mode.catalog().tier("pickaxe", bw.pickaxe()).ifPresent(pick -> inventory.setItem(bw.pickaxeSlot(),
                ShopItems.item(pick, dye(player), mode.texts().i18n(), mode.texts().locale(player))));
        if (bw.axe()) {
            mode.catalog().tier("axe", 1).ifPresent(axe -> inventory.addItem(ShopItems.item(axe, dye(player),
                    mode.texts().i18n(), mode.texts().locale(player))));
        }
        if (bw.shears()) {
            mode.catalog().tier("shears", 1).ifPresent(shears -> inventory.addItem(ShopItems.item(shears,
                    dye(player), mode.texts().i18n(), mode.texts().locale(player))));
        }
        Color color = color(player);
        inventory.setHelmet(ShopItems.leather(Material.LEATHER_HELMET, color));
        inventory.setLeggings(ShopItems.leather(Material.LEATHER_LEGGINGS, color));
        inventory.setBoots(ShopItems.leather(Material.LEATHER_BOOTS, color));
        Optional<ShopItem> chest = bw.armor() > 0 ? mode.catalog().tier("armor", bw.armor()) : Optional.empty();
        inventory.setChestplate(chest.map(item -> ShopItems.item(item, dye(player), mode.texts().i18n(),
                mode.texts().locale(player))).orElseGet(() -> ShopItems.leather(Material.LEATHER_CHESTPLATE, color)));
        applyUpgrades(player);
    }

    /**
     * Team-Upgrades auf Items (Spec 04 §6.1.7): Schärfe auf Schwertern/Äxten, Schutz auf Rüstung, Effizienz auf
     * Werkzeugen (mindestens die eigene Stufe des Items), Federfall auf Schuhen; Schwächefalle zieht ein Level ab.
     */
    public void applyUpgrades(Player player) {
        BwTeam team = mode.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || mode.classic()) {
            return;
        }
        Instant now = Instant.now();
        UUID uuid = player.getUniqueId();
        int weapons = team.upgrades().effective(UpgradeType.WEAPONS, uuid, now);
        int armor = team.upgrades().effective(UpgradeType.ARMOR, uuid, now);
        int mining = team.upgrades().effective(UpgradeType.MINING, uuid, now);
        int feather = team.upgrades().effective(UpgradeType.FEATHER, uuid, now);
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String name = stack.getType().name();
            if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
                level(stack, Enchantment.SHARPNESS, weapons);
            }
            if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.equals("SHEARS")) {
                int own = ShopItems.id(stack).flatMap(mode.catalog()::item)
                        .map(item -> item.enchants().getOrDefault("efficiency", 0)).orElse(0);
                level(stack, Enchantment.EFFICIENCY, Math.max(own, mining));
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            ItemStack piece = inventory.getItem(slot);
            if (piece.getType().isAir()) {
                continue;
            }
            level(piece, Enchantment.PROTECTION, armor);
            if (slot == EquipmentSlot.FEET) {
                level(piece, Enchantment.FEATHER_FALLING, feather);
            }
            inventory.setItem(slot, piece);
        }
    }

    private static void level(ItemStack stack, Enchantment enchantment, int level) {
        stack.editMeta(meta -> {
            meta.removeEnchant(enchantment);
            if (level > 0) {
                meta.addEnchant(enchantment, level, true);
            }
        });
    }

    /** Blockfarbe des Teams ({@code RED} …). */
    public String dye(Player player) {
        GameTeam team = mode.game().teams().teamOf(player.getUniqueId());
        return team == null ? "WHITE" : team.dye();
    }

    private Color color(Player player) {
        GameTeam team = mode.game().teams().teamOf(player.getUniqueId());
        int rgb = team == null ? 0xFFFFFF : ColorText.color(team.colorCode()).value();
        return Color.fromRGB(rgb);
    }

    /** Ressourcenname mit Mehrzahl ({@code Diamanten}). */
    public net.kyori.adventure.text.Component resourceName(Locale locale, Resource resource, int amount) {
        String many = resource.nameKey() + ".many";
        return mode.texts().i18n().render(locale, amount != 1 && mode.texts().i18n().has(many) ? many
                : resource.nameKey());
    }
}
