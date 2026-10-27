package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DurationFormatTest {

    private static Duration d(long days, long hours, long minutes, long seconds) {
        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
    }

    @Test
    void restzeitWieSpec01A4() {
        assertEquals("29d 13h 42m 34s", DurationFormat.format(d(29, 13, 42, 34)));
        assertEquals("2d 23h 9m 58s", DurationFormat.format(d(2, 23, 9, 58)));
        assertEquals("13h 0m 5s", DurationFormat.format(d(0, 13, 0, 5)));
        assertEquals("45s", DurationFormat.format(Duration.ofSeconds(45)));
    }

    @Test
    void grenzfaelleDerAusgabe() {
        assertEquals("0s", DurationFormat.format(Duration.ZERO));
        assertEquals("0s", DurationFormat.format(Duration.ofSeconds(-5)));
        assertEquals("1d 0h 0m 0s", DurationFormat.format(Duration.ofDays(1)));
        assertEquals("1m 0s", DurationFormat.format(Duration.ofSeconds(60)));
        assertEquals("1s", DurationFormat.format(Duration.ofMillis(1999)));
    }

    @Test
    void dauerArgumenteWerdenGelesen() {
        assertEquals(Optional.of(Duration.ofDays(30)), DurationFormat.parse("30d"));
        assertEquals(Optional.of(Duration.ofHours(12)), DurationFormat.parse("12h"));
        assertEquals(Optional.of(d(1, 12, 0, 0)), DurationFormat.parse("1d12h"));
        assertEquals(Optional.of(d(29, 13, 42, 34)), DurationFormat.parse("29d 13h 42m 34s"));
        assertEquals(Optional.of(Duration.ofMinutes(90)), DurationFormat.parse("90M"));
        assertEquals(Optional.of(Duration.ZERO), DurationFormat.parse("0s"));
    }

    @Test
    void ungueltigeDauernSindLeer() {
        assertTrue(DurationFormat.parse("").isEmpty());
        assertTrue(DurationFormat.parse("perm").isEmpty());
        assertTrue(DurationFormat.parse("12").isEmpty());
        assertTrue(DurationFormat.parse("5s3m").isEmpty(), "falsche Reihenfolge");
        assertTrue(DurationFormat.parse("1d1d").isEmpty(), "Einheit doppelt");
        assertTrue(DurationFormat.parse("3w").isEmpty());
        assertTrue(DurationFormat.parse("999999999d999999999h").isPresent(), "große Werte ohne Überlauf");
    }

    @Test
    void formatUndParseSindUmkehrbar() {
        Duration value = d(3, 4, 5, 6);
        assertEquals(Optional.of(value), DurationFormat.parse(DurationFormat.format(value)));
    }
}
