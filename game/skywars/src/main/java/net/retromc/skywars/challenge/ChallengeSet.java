package net.retromc.skywars.challenge;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Aktive Challenges mit XP aus {@code challenges.yml} (Spec 04 §6.2.6, §8.3); fehlende Einträge gelten mit den Werten
 * der Spec. Je Runde wird eine zufällig gewählt.
 *
 * @param xp aktive Challenges → XP bei Erfolg
 */
public record ChallengeSet(Map<Challenge, Integer> xp) {

    /** Alle fünf Challenges der Spec. */
    public static final ChallengeSet DEFAULT = new ChallengeSet(defaults());

    public ChallengeSet {
        xp = Map.copyOf(xp);
    }

    private static Map<Challenge, Integer> defaults() {
        Map<Challenge, Integer> values = new EnumMap<>(Challenge.class);
        for (Challenge challenge : Challenge.values()) {
            values.put(challenge, challenge.defaultXp());
        }
        return values;
    }

    public static ChallengeSet load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        ConfigurationSection section = yaml.getConfigurationSection("challenges");
        Map<Challenge, Integer> values = new EnumMap<>(Challenge.class);
        for (Challenge challenge : Challenge.values()) {
            ConfigurationSection entry = section == null ? null : section.getConfigurationSection(challenge.id());
            if (entry == null || entry.getBoolean("enabled", true)) {
                values.put(challenge, entry == null ? challenge.defaultXp() : entry.getInt("xp",
                        challenge.defaultXp()));
            }
        }
        return new ChallengeSet(values);
    }

    /** XP einer Challenge (0, wenn nicht aktiv). */
    public int xpOf(Challenge challenge) {
        return xp.getOrDefault(challenge, 0);
    }

    /** Zufällige aktive Challenge oder {@code null}, wenn keine aktiv ist. */
    public @Nullable Challenge pick(Random random) {
        List<Challenge> active = new ArrayList<>(xp.keySet());
        active.sort(null);
        return active.isEmpty() ? null : active.get(random.nextInt(active.size()));
    }
}
