package net.retromc.bedwars.upgrade;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.BedWarsMode;
import net.retromc.bedwars.BwTeam;
import net.retromc.bedwars.event.TeamUpgradeEvent;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.bedwars.trap.TrapType;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.shop.Purchase;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiItems;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Upgrade-Shop „Upgrades“ (Spec 04 §5.2, 5 Reihen): Glas hellblau in den Reihen 1, 3 (grau als Trenner) und 5,
 * Upgrades 10–15, Fallen 28–32, Warteschlange 38–40. Bezahlt wird mit Diamanten aus dem Inventar.
 */
public final class UpgradeGui extends Gui {

    /** Slots der Fallen (Reihenfolge {@link TrapType}). */
    static final List<Integer> TRAP_SLOTS = List.of(28, 29, 30, 31, 32);
    /** Slots der Warteschlange. */
    static final List<Integer> QUEUE_SLOTS = List.of(38, 39, 40);

    private final BedWarsMode mode;

    private UpgradeGui(BedWarsMode mode, Component title) {
        super(title, 5);
        this.mode = mode;
    }

    public static void open(BedWarsMode mode, Player player) {
        if (!mode.playing(player)) {
            return;
        }
        Component title = mode.texts().i18n().render(mode.texts().locale(player), "bw.upgrade.title");
        mode.game().services().get(GuiService.class).open(player, new UpgradeGui(mode, title));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        Player viewer = canvas.viewer();
        BwTeam team = mode.teamOf(viewer.getUniqueId()).orElse(null);
        if (team == null) {
            return;
        }
        Locale locale = canvas.locale();
        GameTexts texts = mode.texts();
        for (int column = 0; column < 9; column++) {
            canvas.set(column, GuiItems.border(DyeColor.LIGHT_BLUE));
            canvas.set(18 + column, GuiItems.border(DyeColor.GRAY));
            canvas.set(36 + column, GuiItems.border(DyeColor.LIGHT_BLUE));
        }
        for (UpgradeType type : UpgradeType.values()) {
            int level = team.upgrades().level(type);
            List<Component> lore = new java.util.ArrayList<>(texts.i18n().renderLines(locale,
                    type.nameKey() + ".desc"));
            lore.add(Component.empty());
            lore.add(texts.i18n().render(locale, "bw.upgrade.level", Placeholder.unparsed("n",
                    Integer.toString(level)), Placeholder.unparsed("max", Integer.toString(type.maxLevel()))));
            lore.add(team.upgrades().maxed(type) ? texts.i18n().render(locale, "bw.upgrade.max")
                    : texts.i18n().render(locale, "bw.upgrade.price", Placeholder.unparsed("p",
                            Integer.toString(team.upgrades().nextCost(type)))));
            ItemBuilder item = ItemBuilder.of(Material.valueOf(type.icon())).texts(texts.i18n(), locale)
                    .nameKey(type.nameKey() + ".item").lore(lore).hideAll();
            canvas.button(type.slot(), item.build(), click -> {
                buyUpgrade(click.player(), team, type);
                refresh();
            });
        }
        TrapType[] traps = TrapType.values();
        for (int i = 0; i < traps.length; i++) {
            TrapType trap = traps[i];
            ItemBuilder item = ItemBuilder.of(Material.valueOf(trap.icon())).texts(texts.i18n(), locale)
                    .nameKey(trap.nameKey() + ".item").loreKey(trap.nameKey() + ".desc")
                    .loreKey(trap.cost() == 1 ? "bw.trap.price.one" : "bw.trap.price",
                            Placeholder.unparsed("p", Integer.toString(trap.cost()))).hideAll();
            if (trap == TrapType.MINING_FATIGUE) {
                item.glow();
            }
            canvas.button(TRAP_SLOTS.get(i), item.build(), click -> {
                buyTrap(click.player(), team, trap);
                refresh();
            });
        }
        List<TrapType> queued = team.traps().traps();
        for (int i = 0; i < QUEUE_SLOTS.size(); i++) {
            ItemStack stack;
            if (i < queued.size()) {
                stack = ItemBuilder.of(Material.valueOf(queued.get(i).icon())).texts(texts.i18n(), locale)
                        .nameKey(queued.get(i).nameKey() + ".item").loreKey("bw.trap.position",
                                Placeholder.unparsed("pos", Integer.toString(i + 1))).hideAll().build();
            } else {
                stack = ItemBuilder.of(Material.LIGHT_GRAY_DYE).texts(texts.i18n(), locale).nameKey("bw.trap.free")
                        .loreKey("bw.trap.position", Placeholder.unparsed("pos", Integer.toString(i + 1))).build();
            }
            canvas.set(QUEUE_SLOTS.get(i), stack);
        }
    }

    private void buyUpgrade(Player player, BwTeam team, UpgradeType type) {
        if (!mode.playing(player)) {
            return;
        }
        if (team.upgrades().maxed(type)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int cost = team.upgrades().nextCost(type);
        if (!pay(player, cost, type.nameKey())) {
            return;
        }
        int level = team.upgrades().raise(type);
        Bukkit.getPluginManager().callEvent(new TeamUpgradeEvent(team.team(), type, level, player));
        GameTexts texts = mode.texts();
        for (Player member : mode.online(team)) {
            Component name = texts.i18n().render(texts.locale(member), type.nameKey());
            if (member.equals(player)) {
                texts.send(member, "bw.upgrade.self", Placeholder.component("upgrade", name),
                        Placeholder.unparsed("n", Integer.toString(level)));
            } else {
                texts.send(member, "bw.upgrade.team", texts.name("name", player),
                        Placeholder.component("upgrade", name), Placeholder.unparsed("n", Integer.toString(level)));
            }
            mode.shop().applyUpgrades(member);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void buyTrap(Player player, BwTeam team, TrapType trap) {
        if (!mode.playing(player)) {
            return;
        }
        if (team.traps().full()) {
            mode.texts().send(player, "bw.trap.full");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!pay(player, trap.cost(), trap.nameKey())) {
            return;
        }
        team.traps().add(trap);
        for (Player member : mode.online(team)) {
            mode.texts().send(member, "bw.trap.bought", mode.texts().name("name", player),
                    Placeholder.component("trap", mode.texts().i18n().render(mode.texts().locale(member),
                            trap.nameKey())));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    /** Zieht Diamanten ab; bei Fehlbetrag {@code bw.shop.missing} bzw. {@code bw.shop.nores}. */
    private boolean pay(Player player, int cost, String itemKey) {
        PlayerInventory inventory = player.getInventory();
        List<Integer> slots = new java.util.ArrayList<>();
        List<Integer> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == Material.DIAMOND) {
                slots.add(slot);
                stacks.add(stack.getAmount());
            }
        }
        Purchase.Result result = Purchase.tryBuy(stacks, cost);
        if (!result.paid()) {
            Locale locale = mode.texts().locale(player);
            if (stacks.isEmpty()) {
                mode.texts().send(player, "bw.shop.nores");
            } else {
                mode.texts().send(player, "bw.shop.missing", Placeholder.unparsed("n",
                        Integer.toString(result.missing())), Placeholder.component("res",
                                mode.shop().resourceName(locale, Resource.DIAMOND, result.missing())),
                        Placeholder.component("item", mode.texts().i18n().render(locale, itemKey)));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            int left = result.remaining().get(i);
            inventory.setItem(slots.get(i), left == 0 ? null : inventory.getItem(slots.get(i)).asQuantity(left));
        }
        return true;
    }
}
