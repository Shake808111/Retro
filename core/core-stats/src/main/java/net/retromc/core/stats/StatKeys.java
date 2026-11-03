package net.retromc.core.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.retromc.core.api.stats.StatLine;

/**
 * Schlüssel der {@code extra}-Zähler (Spec 02 §6.7) und ihre Verdichtung über Tage und Runden: Zähler werden addiert,
 * die Bestzeit ist das Minimum, der Level-Rekord das Maximum. Nur so ergibt die Summe der Tagesbuckets das richtige
 * 30-Tage-Fenster.
 */
public final class StatKeys {

    public static final String BEDS = "beds";
    public static final String CORES = "cores";
    public static final String KARMA = "karma";
    public static final String GOALS_CLEAN = "goals_clean";
    public static final String MINIGAMES_PLAYED = "minigames_played";
    public static final String MINIGAMES_WON = "minigames_won";
    public static final String LEVEL_RECORD = "level_record";
    public static final String BEST_TIME_MS = "best_time_ms";
    public static final String CHESTS_OPENED = "chests_opened";
    public static final String BLOCKS_PLACED = "blocks_placed";

    /** Abgeleitete Felder von {@code /stats} (keine Spalten). */
    public static final String KD = "kd";
    public static final String WINRATE = "winrate";
    public static final String BEST_TIME = "best_time";

    /** Werte, bei denen der kleinere zählt (positive Zeiten; 0 = keine Zeit). */
    public static final Set<String> MIN_KEYS = Set.of(BEST_TIME_MS);
    /** Werte, bei denen der größere zählt. */
    public static final Set<String> MAX_KEYS = Set.of(LEVEL_RECORD);

    private StatKeys() {
    }

    /** Verdichtet zwei {@code extra}-Objekte nach den Regeln oben. */
    public static Map<String, Long> merge(Map<String, Long> a, Map<String, Long> b) {
        Map<String, Long> result = new HashMap<>(a);
        b.forEach((key, value) -> result.merge(key, value, (left, right) -> combine(key, left, right)));
        return Map.copyOf(result);
    }

    /** Summe zweier Zeilen desselben Spielers und Modus mit {@link #merge} für {@code extra}. */
    public static StatLine add(StatLine a, StatLine b) {
        if (!a.uuid().equals(b.uuid()) || !a.mode().equals(b.mode())) {
            throw new IllegalArgumentException("Zeilen verschiedener Spieler oder Modi");
        }
        return new StatLine(a.uuid(), a.mode(), a.kills() + b.kills(), a.deaths() + b.deaths(), a.games() + b.games(),
                a.wins() + b.wins(), a.points() + b.points(), merge(a.extra(), b.extra()));
    }

    private static long combine(String key, long left, long right) {
        if (MIN_KEYS.contains(key)) {
            if (left <= 0) {
                return right;
            }
            return right <= 0 ? left : Math.min(left, right);
        }
        if (MAX_KEYS.contains(key)) {
            return Math.max(left, right);
        }
        return left + right;
    }
}
