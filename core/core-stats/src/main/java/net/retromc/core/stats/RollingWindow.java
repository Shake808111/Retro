package net.retromc.core.stats;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rollierendes Fenster und Jobs (Spec 02 §2.1, §6.5): 30 Tage = {@code heute−29 … heute} (Europe/Berlin), also
 * {@code day > current_date − 30}; Rollover täglich 00:00:05 für {@code D = heute − 30}; Reconcile 04:00;
 * Partitionen am 25. für Monat +1, Löschen nach 35 Tagen. Kein Monatsreset.
 */
public final class RollingWindow {

    /** Zeitpunkt des Rollovers (Europe/Berlin). */
    public static final LocalTime ROLLOVER_AT = LocalTime.of(0, 0, 5);
    /** Zeitpunkt des Neuaufbaus (Europe/Berlin). */
    public static final LocalTime RECONCILE_AT = LocalTime.of(4, 0);
    /** Aufbewahrung der Tagesbuckets. */
    public static final int RETENTION_DAYS = 35;
    /** Tag im Monat, ab dem die Partition des Folgemonats angelegt wird. */
    public static final int PARTITION_DAY = 25;
    /** Aufbewahrung von {@code stats.match_player} (Spec 02 O23). */
    public static final int MATCH_RETENTION_DAYS = 180;

    private RollingWindow() {
    }

    /** Erster Tag des Fensters der letzten {@code days} Tage: {@code heute − (days − 1)}. */
    public static LocalDate firstDay(LocalDate today, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Mindestens 1 Tag: " + days);
        }
        return today.minusDays(days - 1L);
    }

    /** Liegt {@code day} im Fenster der letzten {@code days} Tage ({@code day > today − days}, nicht in der Zukunft)? */
    public static boolean contains(LocalDate day, LocalDate today, int days) {
        return !day.isBefore(firstDay(today, days)) && !day.isAfter(today);
    }

    /** Tag, der beim Rollover am Tag {@code today} aus dem 30-Tage-Fenster fällt: {@code today − 30}. */
    public static LocalDate rolloverDay(LocalDate today) {
        return today.minusDays(30);
    }

    /** Monate, deren Partition am Tag {@code today} vorhanden sein muss (aktueller, ab dem 25. auch der nächste). */
    public static List<YearMonth> partitionsToCreate(LocalDate today) {
        Objects.requireNonNull(today, "today");
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        months.add(current);
        if (today.getDayOfMonth() >= PARTITION_DAY) {
            months.add(current.plusMonths(1));
        }
        return List.copyOf(months);
    }

    /** Darf die Partition des Monats gelöscht werden (letzter Tag vor {@code heute − 35})? */
    public static boolean partitionExpired(YearMonth month, LocalDate today) {
        return month.atEndOfMonth().isBefore(today.minusDays(RETENTION_DAYS));
    }
}
