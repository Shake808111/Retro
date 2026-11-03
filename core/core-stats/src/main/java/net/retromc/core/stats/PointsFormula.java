package net.retromc.core.stats;

import java.util.Map;
import java.util.Objects;
import net.retromc.core.api.stats.PlayerMatchStats;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatsMode;

/**
 * Punkteformel eines Modus ({@code stats.mode.points_formula}, Spec 02 §6.4 Schritt 2, §6.7): Summe aus Gewicht ×
 * Zähler, z. B. BedWars {@code 5·kills + 10·beds + 50·wins}, SkyWars {@code 1·kills + 5·wins}. Zähler sind
 * {@code kills}, {@code deaths}, {@code wins}, {@code games} und jeder {@code extra}-Schlüssel. Liefert das Modul
 * {@code points} selbst, gilt dieser Wert.
 */
public final class PointsFormula {

    private PointsFormula() {
    }

    /** Punkte eines Spielers am Rundenende. */
    public static long points(StatsMode mode, PlayerMatchStats player) {
        Objects.requireNonNull(mode, "mode");
        Long own = player.points();
        if (own != null) {
            return own;
        }
        return apply(mode.formula(), key -> switch (key) {
            case StatLine.KILLS -> player.kills();
            case StatLine.DEATHS -> player.deaths();
            case StatLine.WINS -> player.wins();
            case StatLine.GAMES -> 1;
            default -> player.extra().getOrDefault(key, 0L);
        });
    }

    /** Punkte aus aufsummierten Zählern (z. B. zur Prüfung gegen {@code stats.alltime.points}). */
    public static long points(Map<String, Long> weights, StatLine line) {
        return apply(weights, key -> line.value(key).orElse(0));
    }

    private static long apply(Map<String, Long> weights, Counter counter) {
        long sum = 0;
        for (Map.Entry<String, Long> weight : weights.entrySet()) {
            sum = Math.addExact(sum, Math.multiplyExact(weight.getValue(), counter.value(weight.getKey())));
        }
        return sum;
    }

    @FunctionalInterface
    private interface Counter {
        long value(String key);
    }
}
