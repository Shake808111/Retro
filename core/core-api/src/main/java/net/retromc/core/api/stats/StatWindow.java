package net.retromc.core.api.stats;

import java.util.Optional;

/**
 * Zeitfenster einer Statistik-Abfrage (Spec 02 §6.5): die letzten {@code days} Tage (Europe/Berlin, {@code day >
 * current_date − N}) oder gesamt ({@code days = 0}). {@code /stats} = 30 Tage, {@code /statsall} = gesamt,
 * {@code /statsd N} = 1–30 Tage.
 *
 * @param days Fensterlänge in Tagen; 0 = gesamt
 */
public record StatWindow(int days) {

    /** Grenze von {@code /statsd} (Spec 02 §3, §6.14). */
    public static final int MAX_DAYS = 30;

    public static final StatWindow THIRTY_DAYS = new StatWindow(30);
    public static final StatWindow ALL = new StatWindow(0);

    public StatWindow {
        if (days < 0 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Fenster 0–" + MAX_DAYS + " Tage: " + days);
        }
    }

    /** Fenster der letzten {@code days} Tage (1–30). */
    public static StatWindow lastDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Mindestens 1 Tag: " + days);
        }
        return new StatWindow(days);
    }

    public static StatWindow of(StatPeriod period) {
        return period == StatPeriod.ALL ? ALL : THIRTY_DAYS;
    }

    /** {@code true}, wenn {@code days} für {@code /statsd} gültig ist (1–30). */
    public static boolean validDays(int days) {
        return days >= 1 && days <= MAX_DAYS;
    }

    public boolean isAll() {
        return days == 0;
    }

    /** Zeitraum mit Bestenliste ({@code 30d} oder {@code all}); andere Fenster haben keinen Platz (Spec 02 O13). */
    public Optional<StatPeriod> period() {
        if (days == 0) {
            return Optional.of(StatPeriod.ALL);
        }
        return days == StatPeriod.DAYS_30.days() ? Optional.of(StatPeriod.DAYS_30) : Optional.empty();
    }
}
