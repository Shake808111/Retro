package net.retromc.skywars.kit;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Kit-Katalog {@code kits/skywars.yml} (Spec 04 §6.2.5): Reihenfolge = Reihenfolge im Kit-Menü (§5.9). Ohne Server
 * testbar.
 *
 * <pre>
 * kits:
 *   engel:
 *     price: 25000
 *     icon: TOTEM_OF_UNDYING
 *     items: [{material: TOTEM_OF_UNDYING, name: sw.kit.item.angel_totem}]
 *     ability: {type: angel}
 *   teufel:
 *     ability: {type: devil, uses: 3, cooldown: 30, item: {material: BLAZE_POWDER, name: …}}
 * </pre>
 *
 * @param kits Kits in Katalogreihenfolge
 */
public record KitCatalog(List<KitDef> kits) {

    /** Kostenloses Standard-Kit (Kit-Regel 6). */
    public static final String STARTER = "starter";
    /** Plätze im Kit-Menü (Slots 9–35). */
    public static final int MENU_SLOTS = 27;

    public KitCatalog {
        kits = List.copyOf(kits);
        if (kits.stream().noneMatch(kit -> kit.id().equals(STARTER) && kit.free())) {
            throw new IllegalArgumentException("Katalog ohne kostenloses Kit " + STARTER);
        }
        if (kits.stream().map(KitDef::id).distinct().count() != kits.size()) {
            throw new IllegalArgumentException("Kit-IDs doppelt");
        }
    }

    public static KitCatalog load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            throw new IllegalArgumentException("kits fehlt");
        }
        List<KitDef> kits = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection kit = Objects.requireNonNull(section.getConfigurationSection(id), id);
            List<KitItem> items = new ArrayList<>();
            for (Map<?, ?> raw : kit.getMapList("items")) {
                items.add(item(raw, id));
            }
            KitDef.Ability ability = null;
            ConfigurationSection abilitySection = kit.getConfigurationSection("ability");
            if (abilitySection != null) {
                String type = Objects.requireNonNull(abilitySection.getString("type"), id + ".ability.type");
                ConfigurationSection itemSection = abilitySection.getConfigurationSection("item");
                ability = new KitDef.Ability(AbilityType.byId(type).orElseThrow(() -> new IllegalArgumentException(
                        "Unbekannte Fähigkeit " + type + " in " + id)), abilitySection.getInt("uses", 0),
                        abilitySection.getInt("cooldown", 0),
                        itemSection == null ? null : item(itemSection.getValues(true), id),
                        abilitySection.getBoolean("consume", false));
            }
            kits.add(new KitDef(id, kit.getLong("price", 0), kit.getString("icon", "CHEST").toUpperCase(Locale.ROOT),
                    items, ability, kit.getBoolean("legacy", false), kit.getBoolean("classic", false)));
        }
        return new KitCatalog(kits);
    }

    private static KitItem item(Map<?, ?> raw, String kit) {
        Object material = raw.get("material");
        if (material == null) {
            throw new IllegalArgumentException("Item ohne material in " + kit);
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        Object enchanted = raw.get("enchantments");
        if (enchanted instanceof Map<?, ?> map) {
            map.forEach((key, level) -> enchantments.put(String.valueOf(key), Integer.parseInt(String.valueOf(level))));
        } else if (enchanted instanceof ConfigurationSection section) {
            section.getKeys(false).forEach(key -> enchantments.put(key, section.getInt(key)));
        }
        return new KitItem(String.valueOf(material).toUpperCase(Locale.ROOT), integer(raw.get("amount"), 1),
                enchantments, text(raw.get("name")), integer(raw.get("durability"), 0),
                Boolean.parseBoolean(String.valueOf(raw.get("unbreakable"))), text(raw.get("potion")));
    }

    private static int integer(@Nullable Object value, int fallback) {
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public Optional<KitDef> byId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return kits.stream().filter(kit -> kit.id().equals(lower)).findFirst();
    }

    public KitDef starter() {
        return byId(STARTER).orElseThrow();
    }

    /**
     * Wählbare Kits: modern alle aktuellen (frühere nur mit {@code legacyEnabled}); Classic nur Item-Kits und frühere
     * Kits (Spec 04 §6.2.7).
     */
    public List<KitDef> available(boolean classic, boolean legacyEnabled) {
        return kits.stream().filter(kit -> classic ? kit.classic() && !kit.activeAbility()
                : !kit.legacy() || legacyEnabled).toList();
    }
}
