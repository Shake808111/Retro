package net.retromc.game.framework.loot;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Macht aus {@link LootStack}s Items und legt sie in Container (Bukkit, Main-Thread). */
public final class LootItems {

    private LootItems() {
    }

    /** Item zu einem Stapel; unbekannte Verzauberungen/Tränke werden übersprungen. */
    public static ItemStack item(LootStack stack) {
        Material material = Material.matchMaterial(stack.material());
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unbekanntes Material: " + stack.material());
        }
        ItemStack item = ItemStack.of(material, Math.min(stack.amount(), material.getMaxStackSize()));
        stack.enchantments().forEach((key, level) -> enchantment(key).ifPresent(enchantment ->
                item.addUnsafeEnchantment(enchantment, level)));
        String potion = stack.potion();
        if (potion != null) {
            item.editMeta(PotionMeta.class, meta -> applyPotion(meta, potion));
        }
        return item;
    }

    /** Leert {@code inventory} und legt den Inhalt in die Slots. */
    public static void apply(Inventory inventory, Map<Integer, LootStack> content) {
        inventory.clear();
        content.forEach((slot, stack) -> {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(stack));
            }
        });
    }

    public static java.util.Optional<Enchantment> enchantment(String key) {
        return java.util.Optional.ofNullable(RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT))));
    }

    /** {@code typ:stufe[:sekunden]} → Wirkung (Sekunden 0 = sofort). */
    public static void applyPotion(PotionMeta meta, String spec) {
        String[] parts = spec.split(":");
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
        if (type == null) {
            throw new IllegalArgumentException("Unbekannter Trank: " + spec);
        }
        int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        meta.addCustomEffect(new PotionEffect(type, Math.max(1, seconds * 20), level - 1), true);
        meta.setColor(type.getColor());
    }
}
