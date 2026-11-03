package net.retromc.core.api.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Eintrag des Modus-Katalogs {@code stats.mode} (Spec 02 §2.1, Seed §6.7, Migration V2_1 und V2_2).
 *
 * @param id Primärschlüssel ({@code mode_id} der Statistik-Tabellen)
 * @param i18nKey Textschlüssel des Modusnamens, z. B. {@code mode.bedwars}
 * @param color MiniMessage-Farbe, z. B. {@code red}
 * @param gradient Verlauf {@code #RRGGBB:#RRGGBB} (nur MODERN) oder {@code null}
 * @param ranked {@code false} = keine Bestenliste
 * @param formula Gewichte je Zähler ({@code points_formula}); leer = Punkte liefert das Modul
 * @param fields geordnete Stat-Schlüssel für {@code /stats}
 * @param icon Bukkit-Material des Modus-Icons ({@code StatsProfileGui}, Spec 02 §5.4)
 */
public record StatsMode(
        int id,
        ModeKey key,
        String i18nKey,
        String color,
        @Nullable String gradient,
        boolean ranked,
        Metric metric,
        Map<String, Long> formula,
        List<String> fields,
        boolean resettable,
        boolean active,
        String icon) {

    /** Stat-Schlüssel der Zeile „Position im Ranking“. */
    public static final String RANK_FIELD = "rank";

    public StatsMode {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(i18nKey, "i18nKey");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(metric, "metric");
        formula = Map.copyOf(formula);
        fields = List.copyOf(fields);
        Objects.requireNonNull(icon, "icon");
    }

    /** Hat der Modus Bestenlisten ({@code lb:{mode}:*})? {@code metric=none} nie (Spec 02 §6.6). */
    public boolean hasLeaderboard() {
        return ranked && metric != Metric.NONE;
    }

    /** Bewertung der Bestenliste (Spalte {@code metric}, Spec 02 §6.6). */
    public enum Metric {
        /** Score = {@code points × 2^21 + min(wins, 2^21−1)}. */
        POINTS("points"),
        /** Score = Karma ({@code extra.karma}). */
        KARMA("karma"),
        /** Bestzeit: kleinere Zeit ist besser, Score = {@code −millis} ({@code extra.best_time_ms}). */
        TIME("time"),
        /** Kein Ranking (MasterBuilders). */
        NONE("none");

        private final String key;

        Metric(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Optional<Metric> byKey(String key) {
            return Arrays.stream(values()).filter(metric -> metric.key.equals(key)).findFirst();
        }
    }
}
