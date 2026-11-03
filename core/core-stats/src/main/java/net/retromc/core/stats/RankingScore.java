package net.retromc.core.stats;

import java.util.OptionalDouble;
import net.retromc.core.api.stats.StatDelta;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatsMode.Metric;

/**
 * Ranking-Score der Bestenlisten {@code lb:{mode}:{period}} (Spec 02 §6.6). Höherer Score = besserer Platz;
 * Gleichstand entscheidet Valkey lexikografisch über die UUID.
 *
 * <ul>
 *   <li>{@code points}: {@code points × 2^21 + min(wins, 2^21−1)} – bei gleichen Punkten liegen mehr Siege vorne;
 *       exakt als Double, solange {@code points < 2^32}.</li>
 *   <li>{@code karma}: Karma ({@code extra.karma}).</li>
 *   <li>{@code time}: {@code −millis} der Bestzeit (kleinere Zeit ist besser).</li>
 *   <li>{@code none}: keine Bestenliste.</li>
 * </ul>
 */
public final class RankingScore {

    /** Faktor der Punkte: {@code 2^21}. */
    public static final long POINTS_FACTOR = 1L << 21;
    /** Höchstens so viele Siege fließen in den Score ein: {@code 2^21 − 1}. */
    public static final long MAX_WINS = POINTS_FACTOR - 1;

    private RankingScore() {
    }

    /** Score aus Punkten und Siegen ({@code metric=points}). */
    public static double points(long points, long wins) {
        return (double) points * POINTS_FACTOR + Math.min(Math.max(wins, 0), MAX_WINS);
    }

    /** Score einer Zeile; leer, wenn der Spieler nicht in die Liste gehört (keine Zeit, {@code metric=none}). */
    public static OptionalDouble of(Metric metric, StatLine line) {
        return switch (metric) {
            case POINTS -> OptionalDouble.of(points(line.points(), line.wins()));
            case KARMA -> OptionalDouble.of(line.extra().getOrDefault(StatKeys.KARMA, 0L));
            case TIME -> {
                long best = line.extra().getOrDefault(StatKeys.BEST_TIME_MS, 0L);
                yield best > 0 ? OptionalDouble.of(-best) : OptionalDouble.empty();
            }
            case NONE -> OptionalDouble.empty();
        };
    }

    /**
     * Änderung durch eine Runde für {@code ZINCRBY} ({@code points}, {@code karma}); bei {@code time} der neue
     * Kandidat für {@code ZADD GT}; bei {@code none} leer.
     */
    public static OptionalDouble change(Metric metric, StatDelta delta) {
        return switch (metric) {
            case POINTS -> OptionalDouble.of((double) delta.points() * POINTS_FACTOR + delta.wins());
            case KARMA -> OptionalDouble.of(delta.karma());
            case TIME -> delta.bestTimeMillis() > 0 ? OptionalDouble.of(-delta.bestTimeMillis())
                    : OptionalDouble.empty();
            case NONE -> OptionalDouble.empty();
        };
    }

    /** {@code true}, wenn Änderungen addiert werden ({@code ZINCRBY}); {@code time} nutzt {@code ZADD GT}. */
    public static boolean additive(Metric metric) {
        return metric == Metric.POINTS || metric == Metric.KARMA;
    }

    /** Punkte aus einem {@code points}-Score. */
    public static long pointsOf(double score) {
        return Math.floorDiv((long) score, POINTS_FACTOR);
    }

    /** Siege aus einem {@code points}-Score (gedeckelt bei {@link #MAX_WINS}). */
    public static long winsOf(double score) {
        return Math.floorMod((long) score, POINTS_FACTOR);
    }

    /** Anzeigewert einer Bestenliste: Punkte, Karma oder Bestzeit in Millisekunden. */
    public static long displayValue(Metric metric, double score) {
        return switch (metric) {
            case POINTS -> pointsOf(score);
            case KARMA -> (long) score;
            case TIME -> (long) -score;
            case NONE -> 0;
        };
    }
}
