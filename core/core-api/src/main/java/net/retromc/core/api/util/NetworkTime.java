package net.retromc.core.api.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Netzwerk-Zeitzone Europe/Berlin für Tageswechsel: Clan-Umbenennungen (Spec 01 §6.6), {@code /joinme}
 * (Spec 01 §6.17), Tagesbuckets {@code stats.daily} (Spec 02 §2.1) und Jobs (Spec 08 §6.10).
 */
public final class NetworkTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private NetworkTime() {
    }

    /** Kalendertag in Europe/Berlin. */
    public static LocalDate day(Instant instant) {
        return Objects.requireNonNull(instant, "instant").atZone(ZONE).toLocalDate();
    }

    /** Beginn des Tages (00:00 Europe/Berlin). */
    public static Instant startOfDay(LocalDate day) {
        return Objects.requireNonNull(day, "day").atStartOfDay(ZONE).toInstant();
    }
}
