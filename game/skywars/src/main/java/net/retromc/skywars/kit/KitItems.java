package net.retromc.skywars.kit;

import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.TextDecoration;
import net.retromc.core.api.BuildInfo;
import net.retromc.game.framework.loot.LootItems;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Baut die Items eines Kits und legt sie an (Spec 04 §4.11: Kit-Inhalt, Fähigkeits-Item in Slot 9). */
public final class KitItems {

    /** PDC-Schlüssel des Fähigkeits-Items (Wert = Kit-ID). */
    public static final NamespacedKey ABILITY = new NamespacedKey(BuildInfo.BRAND_ID, "sw_ability");
    /** Slot des Fähigkeits-Items (Hotbar 9, AN). */
    public static final int ABILITY_SLOT = 8;

    private KitItems() {
    }

    public static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unbekanntes Material: " + name);
        }
        return material;
    }

    /** Item zu einem Kit-Eintrag. */
    public static ItemStack item(KitItem item, I18n i18n, Locale locale) {
        Material material = material(item.material());
        ItemStack stack = ItemStack.of(material, Math.min(item.amount(), material.getMaxStackSize()));
        item.enchantments().forEach((key, level) -> LootItems.enchantment(key).ifPresent(enchantment ->
                stack.addUnsafeEnchantment(enchantment, level)));
        stack.editMeta(meta -> {
            String nameKey = item.nameKey();
            if (nameKey != null) {
                meta.customName(i18n.render(locale, nameKey).decoration(TextDecoration.ITALIC, false));
            }
            if (item.unbreakable()) {
                meta.setUnbreakable(true);
            }
            if (item.durability() > 0 && meta instanceof Damageable damageable && material.getMaxDurability() > 0) {
                damageable.setDamage(Math.max(0, material.getMaxDurability() - item.durability()));
            }
            String potion = item.potion();
            if (potion != null && meta instanceof PotionMeta potionMeta) {
                LootItems.applyPotion(potionMeta, potion);
            }
        });
        return stack;
    }

    /** Fähigkeits-Item mit Kit-Kennung. */
    public static ItemStack abilityItem(KitDef kit, KitItem item, I18n i18n, Locale locale) {
        ItemStack stack = item(item, i18n, locale);
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(ABILITY, PersistentDataType.STRING, kit.id()));
        return stack;
    }

    /** Kit-ID eines Fähigkeits-Items oder leer. */
    public static Optional<String> abilityKit(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ABILITY,
                PersistentDataType.STRING));
    }

    /** Legt das Kit an: Rüstung in die Rüstungsslots (wenn frei), Rest ins Inventar, Fähigkeits-Item in Slot 9. */
    public static void apply(Player player, KitDef kit, I18n i18n, Locale locale) {
        PlayerInventory inventory = player.getInventory();
        for (KitItem entry : kit.items()) {
            ItemStack stack = item(entry, i18n, locale);
            EquipmentSlot slot = armorSlot(stack.getType());
            if (slot != null && isEmpty(inventory.getItem(slot))) {
                inventory.setItem(slot, stack);
            } else {
                inventory.addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(),
                        rest));
            }
        }
        KitDef.Ability ability = kit.ability();
        if (ability != null && ability.item() != null) {
            ItemStack previous = inventory.getItem(ABILITY_SLOT);
            inventory.setItem(ABILITY_SLOT, abilityItem(kit, ability.item(), i18n, locale));
            if (!isEmpty(previous)) {
                inventory.addItem(previous);
            }
        }
    }

    /** Rüstungsslot eines Materials oder {@code null}. */
    public static @Nullable EquipmentSlot armorSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        return name.endsWith("_BOOTS") ? EquipmentSlot.FEET : null;
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }
}
