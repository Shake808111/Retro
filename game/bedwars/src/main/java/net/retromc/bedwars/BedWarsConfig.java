package net.retromc.bedwars;

import java.time.Duration;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Einstellungen aus {@code config.yml}, Abschnitt {@code bedwars} (Spec 04 §8.3).
 *
 * @param classic        Classic-Preset (Spec 04 §6.1.9)
 * @param respawnSeconds Respawn-Zeit (5 s)
 * @param spawnProtection Radius ohne Blockplatzierung um den Team-Spawn
 * @param maxMinutes     Rundenhöchstdauer bis alle Betten zerstört werden
 * @param drawMinutes    danach bis zum Unentschieden
 * @param baseRadius     Radius der Basis-Effekte
 * @param mapColon       Board-Option {@code board.mapColon}
 * @param rewards        XP/Coins
 * @param generators     Takte in Sekunden ({@code copper}, {@code iron}, {@code bronze}, {@code classic-iron},
 *                       {@code classic-gold})
 * @param cooldowns      Item-Abklingzeiten in Sekunden
 * @param items          Werte der Sonder-Items
 */
public record BedWarsConfig(boolean classic, int respawnSeconds, int spawnProtection, int maxMinutes,
        int drawMinutes, int baseRadius, boolean mapColon, BedWarsRewards rewards, Map<String, Integer> generators,
        Map<String, Integer> cooldowns, Map<String, Double> items) {

    public BedWarsConfig {
        generators = Map.copyOf(generators);
        cooldowns = Map.copyOf(cooldowns);
        items = Map.copyOf(items);
    }

    public static BedWarsConfig read(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("bedwars");
        if (section == null) {
            section = config.createSection("bedwars");
        }
        ConfigurationSection rewards = section.getConfigurationSection("rewards");
        BedWarsRewards values = rewards == null ? BedWarsRewards.DEFAULT : new BedWarsRewards(
                rewards.getInt("kill", 25), rewards.getInt("bed", 10), rewards.getInt("win", 50),
                rewards.getInt("survived", 20), rewards.getInt("per-seconds", 30),
                rewards.getDouble("coin-factor", 2.5));
        return new BedWarsConfig("classic".equalsIgnoreCase(section.getString("preset", "modern")),
                section.getInt("respawn-seconds", 5), section.getInt("spawn-protection", 3),
                section.getInt("max-minutes", 60), section.getInt("draw-minutes", 10),
                section.getInt("base-radius", 12), section.getBoolean("board-map-colon", false), values,
                ints(section.getConfigurationSection("generators")), ints(section.getConfigurationSection("cooldowns")),
                doubles(section.getConfigurationSection("items")));
    }

    private static Map<String, Integer> ints(@Nullable ConfigurationSection section) {
        Map<String, Integer> values = new java.util.HashMap<>();
        if (section != null) {
            section.getKeys(false).forEach(key -> values.put(key, section.getInt(key)));
        }
        return values;
    }

    private static Map<String, Double> doubles(@Nullable ConfigurationSection section) {
        Map<String, Double> values = new java.util.HashMap<>();
        if (section != null) {
            section.getKeys(false).forEach(key -> values.put(key, section.getDouble(key)));
        }
        return values;
    }

    public Duration generator(String key, int fallback) {
        return Duration.ofSeconds(generators.getOrDefault(key, fallback));
    }

    public Duration cooldown(String item) {
        return Duration.ofSeconds(cooldowns.getOrDefault(item, 0));
    }

    public double item(String key, double fallback) {
        return items.getOrDefault(key, fallback);
    }

    /** Statistik-Modus. */
    public String modeId() {
        return classic ? "bedwars_classic" : "bedwars";
    }
}
