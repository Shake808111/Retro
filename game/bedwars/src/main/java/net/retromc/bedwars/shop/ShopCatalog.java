package net.retromc.bedwars.shop;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.retromc.bedwars.spawner.Resource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Shop-Katalog aus {@code shop.yml} (Spec 04 §6.1.6, §8.2 {@code ShopCatalog}): neun Kategorien (0 = Schnellkauf),
 * je höchstens 21 Waren. Das Classic-Preset (§6.1.9) übernimmt den Katalog mit Bronze statt Kupfer und den
 * Abweichungen unter {@code classic:} je Ware ({@code classic: false} = nicht im Classic-Shop, {@code modern: false}
 * = nur im Classic-Shop).
 */
public final class ShopCatalog {

    /** Kategorie-IDs in Standard-Reihenfolge (Textschlüssel {@code bw.shop.cat.<id>}). */
    public static final List<String> CATEGORIES = List.of("quick", "blocks", "armor", "tools", "weapons", "potions",
            "special", "explosives", "misc");
    /** Icons der Kategorien (Spec 04 §5.1). */
    public static final List<String> ICONS = List.of("AMETHYST_CLUSTER", "OBSIDIAN", "CHAINMAIL_CHESTPLATE",
            "IRON_PICKAXE", "IRON_SWORD", "POTION", "ENDER_PEARL", "TNT", "BRICKS");
    /** Waren je Kategorie (3 × 7). */
    public static final int PER_CATEGORY = 21;

    private final List<ShopItem> items;
    private final Map<String, ShopItem> byId;

    private ShopCatalog(List<ShopItem> items) {
        this.items = List.copyOf(items);
        Map<String, ShopItem> index = new LinkedHashMap<>();
        for (ShopItem item : items) {
            if (index.put(item.id(), item) != null) {
                throw new IllegalArgumentException("Ware doppelt: " + item.id());
            }
        }
        this.byId = Collections.unmodifiableMap(index);
        for (int category = 1; category < CATEGORIES.size(); category++) {
            if (category(category).size() > PER_CATEGORY) {
                throw new IllegalArgumentException("Mehr als 21 Waren in " + CATEGORIES.get(category));
            }
        }
    }

    /** Liest {@code shop.yml}; {@code classic} wendet das Classic-Preset an. */
    public static ShopCatalog load(Reader reader, boolean classic) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("items");
        if (root == null) {
            throw new IllegalArgumentException("shop.yml ohne items");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            ConfigurationSection override = null;
            if (!classic && !section.getBoolean("modern", true)) {
                continue;
            }
            if (classic) {
                if (section.isBoolean("classic") && !section.getBoolean("classic")) {
                    continue;
                }
                override = section.getConfigurationSection("classic");
            }
            items.add(item(id, section, override, classic));
        }
        return new ShopCatalog(items);
    }

    private static ShopItem item(String id, ConfigurationSection section, ConfigurationSection override,
            boolean classic) {
        ConfigurationSection source = override != null ? override : section;
        String priceText = source.getString("price", section.getString("price", ""));
        String[] price = priceText.strip().split("\\s+");
        int amount = 0;
        Resource currency = Resource.COPPER;
        if (price.length == 2) {
            amount = Integer.parseInt(price[0]);
            currency = Resource.byId(price[1]).orElseThrow(() -> new IllegalArgumentException(id + ": Währung "
                    + price[1]));
        } else if (!priceText.isBlank() && !priceText.equalsIgnoreCase("start")) {
            throw new IllegalArgumentException(id + ": Preis \"<Menge> <Währung>\": " + priceText);
        }
        if (classic && currency == Resource.COPPER) {
            currency = Resource.BRONZE;
        }
        Map<String, Integer> enchants = new TreeMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchants");
        if (enchantSection != null) {
            enchantSection.getKeys(false).forEach(key -> enchants.put(key.toLowerCase(Locale.ROOT),
                    enchantSection.getInt(key)));
        }
        return new ShopItem(id, section.getInt("category"), source.getString("material",
                section.getString("material", "STONE")), source.getInt("amount", section.getInt("amount", 1)),
                currency, amount, section.getBoolean("permanent", false), section.getString("group"),
                section.getInt("tier", 0), classic ? -1 : section.getInt("quick", -1), section.getString("special"),
                enchants, section.getString("potion"), section.getBoolean("lore", false));
    }

    public List<ShopItem> items() {
        return items;
    }

    public Optional<ShopItem> item(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Waren einer Kategorie (1–8) in Katalog-Reihenfolge. */
    public List<ShopItem> category(int category) {
        return items.stream().filter(item -> item.category() == category).toList();
    }

    /** Standard-Schnellkauf {@code feld → id}. */
    public Map<Integer, String> defaultQuickshop() {
        Map<Integer, String> slots = new TreeMap<>();
        items.stream().filter(item -> item.quick() >= 0).forEach(item -> slots.put(item.quick(), item.id()));
        return Collections.unmodifiableMap(slots);
    }

    /** Ware einer Gruppe mit Stufe. */
    public Optional<ShopItem> tier(String group, int tier) {
        return items.stream().filter(item -> group.equals(item.group()) && item.tier() == tier).findFirst();
    }
}
