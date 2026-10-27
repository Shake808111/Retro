package net.retromc.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Jedes Format-Beispiel aus Spec 02 §6.8/§9 #1 und Spec 01 §6.10/§9 A4 als Testfall. */
class FormatsTest {

    private final Formats de = Formats.of(Locale.GERMANY);
    private final Formats en = Formats.of(Locale.US);

    @Test
    void ganzeZahlenMitTausenderpunkt() {
        assertEquals("10.560", de.integer(10_560));
        assertEquals("1.355.470", de.integer(1_355_470));
        assertEquals("4.950", de.integer(4_950));
        assertEquals("455", de.integer(455));
        assertEquals("0", de.integer(0));
        assertEquals("-1.000", de.integer(-1_000));
        assertEquals("-9.223.372.036.854.775.808", de.integer(Long.MIN_VALUE));
        assertEquals("10,560", en.integer(10_560));
        // Dialekte nutzen die de_DE-Formate.
        assertEquals("10.560", Formats.of(Language.BAR).integer(10_560));
        assertEquals("10.560", Formats.of(Language.GSW).integer(10_560));
    }

    @Test
    void legacyZahlen() {
        // Spec 02 §6.8: LEGACY ganze Zahlen mit Komma (11,877); Hologramm LEGACY ohne Trenner.
        assertEquals("11,877", de.integer(11_877, UiEra.LEGACY));
        assertEquals("11.877", de.integer(11_877, UiEra.MODERN));
        assertEquals("11877", de.hologramInteger(11_877, UiEra.LEGACY));
        assertEquals("11.877", de.hologramInteger(11_877, UiEra.MODERN));
    }

    @Test
    void killDeathNachStats() {
        assertEquals("11.21", de.statsKd(314, 28));
        assertEquals("1.88", de.statsKd(186, 99));
        assertEquals("0", de.statsKd(0, 0));
        assertEquals("5", de.statsKd(5, 0));
        assertEquals("1.5", de.statsKd(3, 2));
        assertEquals("11.21", en.statsKd(314, 28));
        assertEquals("1.33", de.hologramKd(4, 3));
    }

    @Test
    void siegesquoteNachStatsMitPunkt() {
        assertEquals("84.85 %", de.statsWinrate(140, 165));
        assertEquals("25 %", de.statsWinrate(33, 132));
        assertEquals("0 %", de.statsWinrate(0, 1));
        assertEquals("0 %", de.statsWinrate(0, 0));
        assertEquals("100 %", de.statsWinrate(7, 7));
        // Die /stats-Dezimalregel gilt in allen Sprachen (Spec 02 §6.13 Nr. 5).
        assertEquals("84.85 %", en.statsWinrate(140, 165));
    }

    @Test
    void siegesquoteImHologrammUndScoreboard() {
        assertEquals("27,59%", de.hologramWinrate(16, 58));
        assertEquals("25%", de.hologramWinrate(33, 132));
        assertEquals("27.59%", en.hologramWinrate(16, 58));
        assertEquals("84,21 %", de.scoreboardWinrate(16, 19));
        assertEquals("69,49 %", de.scoreboardWinrate(41, 59));
        assertEquals("0,00 %", de.scoreboardWinrate(0, 0));
        assertEquals("69.49 %", en.scoreboardWinrate(41, 59));
    }

    @Test
    void clanWarSiegesquoteMitKomma() {
        // Spec 01 §6.6: wins/games·100 mit 2 Nachkommastellen, deutsches Komma (Text " %" steht im Key).
        assertEquals("66,67", de.fixed2(Formats.winrate(2, 3)));
        assertEquals("50,00", de.fixed2(Formats.winrate(1, 2)));
    }

    @Test
    void rundungHalfUp() {
        assertEquals(new BigDecimal("0.67"), Formats.kd(2, 3));
        assertEquals("0.13", de.statsDecimal(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("100.50"), Formats.winrate(201, 200));
        assertThrows(IllegalArgumentException.class, () -> Formats.kd(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Formats.winrate(1, -1));
    }

    @Test
    void spielzeitInStunden() {
        assertEquals(455, Formats.playtimeHours(455 * 3600 + 3599));
        assertEquals(1, Formats.playtimeHours(3600));
        assertEquals(0, Formats.playtimeHours(3599));
    }

    @Test
    void restzeitAbGroessterEinheit() {
        assertEquals("29d 13h 42m 34s", de.duration(Duration.ofDays(29).plusHours(13).plusMinutes(42).plusSeconds(34)));
        assertEquals("2d 23h 9m 58s", de.duration(Duration.ofDays(2).plusHours(23).plusMinutes(9).plusSeconds(58)));
        assertEquals("13h 0m 5s", de.duration(Duration.ofHours(13).plusSeconds(5)));
        assertEquals("13h 42m 34s", de.duration(Duration.ofHours(13).plusMinutes(42).plusSeconds(34)));
        assertEquals("45s", de.duration(Duration.ofSeconds(45)));
        assertEquals("1m 0s", de.duration(Duration.ofSeconds(60)));
        assertEquals("0s", de.duration(Duration.ofMillis(900)));
        assertEquals("0s", de.duration(Duration.ofSeconds(-5)));
        assertEquals("29d 13h 42m 34s", en.duration(Duration.ofSeconds(29 * 86_400 + 13 * 3_600 + 42 * 60 + 34)));
    }

    @Test
    void countdownMinutenSekunden() {
        assertEquals("00:59", de.countdown(59));
        assertEquals("05:00", de.countdown(300));
        assertEquals("59:59", de.countdown(3_599));
        assertEquals("01:00:00", de.countdown(3_600));
        assertEquals("02:03:04", de.countdown(Duration.ofSeconds(2 * 3_600 + 3 * 60 + 4)));
        assertEquals("00:00", de.countdown(-3));
    }

    @Test
    void datumUndUhrzeitInBerlin() {
        // 2026-10-25 00:30 UTC = 02:30 MESZ; 2026-10-26 00:30 UTC = 01:30 MEZ (Zeitumstellung).
        Instant summer = Instant.parse("2026-10-25T00:30:00Z");
        Instant winter = Instant.parse("2026-10-26T00:30:00Z");
        assertEquals("25.10.2026", de.date(summer));
        assertEquals("02:30", de.time(summer));
        assertEquals("01:30", de.time(winter));
        assertEquals("25.10.2026 02:30", de.dateTime(summer));
        assertEquals("12/31/2026", en.date(Instant.parse("2026-12-31T12:00:00Z")));
        assertEquals("01.01.2027", de.date(Instant.parse("2026-12-31T23:30:00Z")));
    }

    @Test
    void saisonMitEnglischenMonaten() {
        assertEquals("Jul-Sep 2026", de.season(YearMonth.of(2026, 7), YearMonth.of(2026, 9)));
        assertEquals("Jul-Sep 2026", en.season(YearMonth.of(2026, 7), YearMonth.of(2026, 9)));
    }
}
