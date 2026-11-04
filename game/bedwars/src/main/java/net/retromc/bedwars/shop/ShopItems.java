package net.retromc.bedwars.shop;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.core.api.BuildInfo;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

/**
 * Baut BedWars-Items: gekaufte Waren (mit PDC-Markierung {@code <brand>:bw_item = id}), Ressourcen mit Namen
 * ({@code §cKupfer} …) und Lederrüstung in Teamfarbe (unzerstörbar).
 */
public final class ShopItems {

    /** PDC-Schlüssel der Waren-ID. */
    public static final NamespacedKey ITEM = new NamespacedKey(BuildInfo.BRAND_ID, "bw_item");

    private ShopItems() {
    }

    /** Ware für ein Team (Name in der Sprache des Käufers). */
    public static ItemStack item(ShopItem item, String teamDye, I18n i18n, Locale locale) {
        Material material = material(item.material(teamDye));
        ItemStack stack = ItemStack.of(material, item.amount());
        stack.editMeta(meta -> {
            if (item.special() != null || item.group() != null || !item.enchants().isEmpty()
                    || item.potion() != null) {
                meta.customName(i18n.render(locale, item.nameKey()).decoration(TextDecoration.ITALIC, false));
            }
            item.enchants().forEach((key, level) -> enchantment(key).ifPresent(enchantment ->
                    meta.addEnchant(enchantment, level, true)));
            if (item.permanent()) {
                meta.setUnbreakable(true);
            }
            if (meta instanceof PotionMeta potion && item.potion() != null) {
                applyPotion(potion, item.potion());
            }
            if (meta instanceof LeatherArmorMeta leather && item.special() != null) {
                leather.setColor(Color.fromRGB(0x404040));
            }
            meta.getPersistentDataContainer().set(ITEM, PersistentDataType.STRING, item.id());
        });
        return stack;
    }

    /** Waren-ID eines Items oder leer. */
    public static Optional<String> id(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ITEM,
                PersistentDataType.STRING));
    }

    /** Ressourcen-Item mit Namen (Spawner, Übertragung). */
    public static ItemStack resource(Resource resource, int amount, I18n i18n, Locale locale) {
        ItemStack stack = ItemStack.of(material(resource.material()), Math.max(1, amount));
        stack.editMeta(meta -> meta.customName(ColorText.colored(resource.color(),
                plain(i18n, locale, resource.nameKey())).decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    /** Lederrüstung in Teamfarbe, unzerstörbar. */
    public static ItemStack leather(Material material, Color color) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.setUnbreakable(true);
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(color);
            }
        });
        return stack;
    }

    /** Unzerstörbares Start-/Permanent-Item. */
    public static ItemStack unbreakable(Material material) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> meta.setUnbreakable(true));
        return stack;
    }

    public static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            throw new IllegalArgumentException("Unbekanntes Material: " + name);
        }
        return material;
    }

    public static Optional<Enchantment> enchantment(String key) {
        return Optional.ofNullable(RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT))));
    }

    /** {@code type:stufe:sekunden} → Trankwirkung (Sekunden 0 = sofort). */
    static void applyPotion(PotionMeta meta, String spec) {
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

    private static String plain(I18n i18n, Locale locale, String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(i18n.render(locale, key));
    }

    /** Rote Score-Zahl wie Vanilla. */
    public static Component score(int value) {
        return Component.text(value, net.kyori.adventure.text.format.NamedTextColor.RED);
    }
}
