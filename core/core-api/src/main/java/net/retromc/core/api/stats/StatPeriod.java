package net.retromc.core.api.stats;

import java.util.Arrays;
import java.util.Optional;

/**
 * Zeitraum der Statistik (Spec 02 §2.2, §6.5): rollierende 30 Tage oder gesamt. Der Schlüssel steht in
 * {@code lb:{mode}:{period}}, {@code st:*}, {@code top:*} und {@code core.display_anchor.period}.
 */
public enum StatPeriod {
    /** Tage {@code heute−29 … heute} (Europe/Berlin). */
    DAYS_30("30d", 30),
    /** Gesamtwerte ({@code stats.alltime}). */
    ALL("all", 0);

    private final String key;
    private final int days;

    StatPeriod(String key, int days) {
        this.key = key;
        this.days = days;
    }

    public String key() {
        return key;
    }

    /** Fensterlänge in Tagen; 0 = unbegrenzt. */
    public int days() {
        return days;
    }

    public static Optional<StatPeriod> byKey(String key) {
        return Arrays.stream(values()).filter(period -> period.key.equals(key)).findFirst();
    }
}
