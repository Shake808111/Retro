package net.retromc.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.retromc.core.api.stats.CoinPayout;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.PlayerMatchStats;
import net.retromc.core.api.stats.StatDelta;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsMode.Metric;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.i18n.Formats;
import net.retromc.core.i18n.Language;
import org.junit.jupiter.api.Test;

/** Abnahme Spec 02 §9 #1–#4, #10 (Unit), #12 sowie Fenster, Bericht und Gleichstand. */
class StatsFormulasTest {

    static final StatsMode BEDWARS = mode(1, "bedwars", Metric.POINTS, Map.of("kills", 5L, "beds", 10L, "wins", 50L),
            List.of("rank", "points", "kills", "deaths", "kd", "games", "wins", "winrate", "beds"), true);
    static final StatsMode SKYWARS = mode(2, "skywars", Metric.POINTS, Map.of("kills", 1L, "wins", 5L),
            List.of("rank", "points", "kills", "deaths", "kd", "games", "wins", "winrate"), true);
    static final StatsMode MASTERBUILDERS = mode(10, "masterbuilders", Metric.NONE, Map.of(),
            List.of("games", "wins", "losses", "points"), false);

    static StatsMode mode(int id, String key, Metric metric, Map<String, Long> formula, List<String> fields,
            boolean ranked) {
        return new StatsMode(id, ModeKey.of(key), "mode." + key, "red", null, ranked, metric, formula, fields, true,
                true, "PAPER");
    }

    static PlayerMatchStats player(int kills, int deaths, boolean win, Map<String, Long> extra) {
        return new PlayerMatchStats(UUID.randomUUID(), 1, null, kills, deaths, win, true, 0, null, extra);
    }

    @Test
    void abnahme1FormateJeOberflaeche() {
        Formats de = Formats.of(Language.DE_DE);
        assertEquals("10.560", de.integer(10_560));
        assertEquals("11.21", de.statsKd(314, 28));
        assertEquals("1.88", de.statsKd(186, 99));
        assertEquals("0", de.statsKd(0, 0));
        assertEquals("84.85 %", de.statsWinrate(140, 165));
        assertEquals("25 %", de.statsWinrate(33, 132));
        assertEquals("0 %", de.statsWinrate(0, 1));
        assertEquals("27,59%", de.hologramWinrate(16, 58));
        assertEquals("84,21 %", de.scoreboardWinrate(16, 19));
        assertEquals("69,49 %", de.scoreboardWinrate(41, 59));
        // Die /stats-Dezimalregel (Punkt) gilt in allen Sprachen (§6.13 Nr. 5).
        assertEquals("84.85 %", Formats.of(Locale.US).statsWinrate(140, 165));
    }

    @Test
    void abnahme2Punkteformeln() {
        PlayerMatchStats bedwars = new PlayerMatchStats(UUID.randomUUID(), null, null, 314, 0, false, false, 0, null,
                Map.of("beds", 199L));
        assertEquals(5 * 314 + 10 * 199, PointsFormula.points(BEDWARS, bedwars));
        StatLine bedwarsTotal = new StatLine(UUID.randomUUID(), BEDWARS.key(), 314, 28, 165, 140, 0,
                Map.of("beds", 199L));
        assertEquals(10_560, PointsFormula.points(BEDWARS.formula(), bedwarsTotal));
        assertEquals(25, PointsFormula.points(BEDWARS, player(3, 0, false, Map.of("beds", 1L))));
        StatLine skywarsTotal = new StatLine(UUID.randomUUID(), SKYWARS.key(), 186, 99, 132, 33, 0, Map.of());
        assertEquals(351, PointsFormula.points(SKYWARS.formula(), skywarsTotal));
        assertEquals(1 + 5, PointsFormula.points(SKYWARS, player(1, 0, true, Map.of())));
    }

    @Test
    void modulpunkteHabenVorrang() {
        PlayerMatchStats own = new PlayerMatchStats(UUID.randomUUID(), null, null, 9, 0, true, true, 0, 77L, Map.of());
        assertEquals(77, PointsFormula.points(BEDWARS, own));
    }

    @Test
    void abnahme3CoinBoost() {
        assertEquals(142, CoinBoost.bonus(475, 3));
        assertEquals(95, CoinBoost.bonus(239, 5));
        assertEquals(31, CoinBoost.bonus(314, 1));
        assertEquals(26, CoinBoost.bonus(134, 2));
        assertEquals(6, CoinBoost.bonus(15, 6));
        CoinPayout none = CoinBoost.payout(UUID.randomUUID(), 0, List.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, none.boost());
        assertEquals(4, none.boosterCount());
    }

    @Test
    void boostZaehltEmpfaengerOhneNamen() {
        UUID self = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        CoinPayout payout = CoinBoost.payout(self, 314, List.of(other, self));
        assertEquals(2, payout.boosterCount());
        assertEquals(62, payout.boost());
        assertEquals(List.of(other), payout.boosters());
        assertEquals(0, CoinBoost.payout(self, 100, List.of()).boost());
    }

    @Test
    void abnahme4Namensliste() {
        assertEquals("A", CoinBoost.Names.of(List.of("A")).plain());
        assertEquals("A, B", CoinBoost.Names.of(List.of("A", "B")).plain());
        assertEquals("A, B und 1 weiteren", CoinBoost.Names.of(List.of("A", "B", "C")).plain());
        assertEquals("A, B und 5 weiteren", CoinBoost.Names.of(List.of("A", "B", "C", "D", "E", "F", "G")).plain());
        assertEquals("coins.names.single", CoinBoost.Names.of(List.of("A")).key());
        assertEquals("coins.names.pair", CoinBoost.Names.of(List.of("A", "B")).key());
        assertEquals("coins.names.more", CoinBoost.Names.of(List.of("A", "B", "C")).key());
    }

    @Test
    void abnahme10MehrSiegeBeiGleichenPunkten() {
        double fewer = RankingScore.points(1_000, 10);
        double more = RankingScore.points(1_000, 11);
        assertTrue(more > fewer);
        assertTrue(RankingScore.points(1_001, 0) > RankingScore.points(1_000, RankingScore.MAX_WINS + 5));
        assertEquals(1_000, RankingScore.pointsOf(more));
        assertEquals(11, RankingScore.winsOf(more));
        // exakt als Double unter 2^53
        double big = RankingScore.points(4_000_000_000L, RankingScore.MAX_WINS);
        assertEquals(4_000_000_000L, RankingScore.pointsOf(big));
        assertEquals(RankingScore.MAX_WINS, RankingScore.winsOf(big));
    }

    @Test
    void scoreJeMetrik() {
        UUID uuid = UUID.randomUUID();
        StatLine karma = new StatLine(uuid, ModeKey.TTT, 0, 0, 1, 0, 0, Map.of("karma", 40L));
        assertEquals(40, RankingScore.of(Metric.KARMA, karma).orElseThrow());
        StatLine time = new StatLine(uuid, ModeKey.BINGO_X1, 0, 0, 1, 1, 0, Map.of("best_time_ms", 90_000L));
        assertEquals(-90_000, RankingScore.of(Metric.TIME, time).orElseThrow());
        assertTrue(RankingScore.of(Metric.TIME, StatLine.empty(uuid, ModeKey.BINGO_X1)).isEmpty());
        assertTrue(RankingScore.of(Metric.NONE, karma).isEmpty());
        assertEquals(90_000, RankingScore.displayValue(Metric.TIME, -90_000));
        assertEquals(5 * RankingScore.POINTS_FACTOR + 1,
                RankingScore.change(Metric.POINTS, new StatDelta(uuid, 5, 1, 0, 0)).orElseThrow());
        assertFalse(RankingScore.additive(Metric.TIME));
    }

    @Test
    void abnahme12TogglestatsErstAb100Stunden() {
        assertFalse(StatsRules.mayToggle(99 * 3_600));
        assertFalse(StatsRules.mayToggle(359_999));
        assertTrue(StatsRules.mayToggle(360_000));
    }

    @Test
    void sichtbarkeitVersteckterStats() {
        UUID viewer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertTrue(StatsRules.mayView(viewer, target, false, false));
        assertFalse(StatsRules.mayView(viewer, target, true, false));
        assertTrue(StatsRules.mayView(target, target, true, false));
        assertTrue(StatsRules.mayView(viewer, target, true, true));
    }

    @Test
    void resetPruefungen() {
        long noToken = 0;
        assertEquals(Optional.of("reset.wrong-place"), StatsRules.resetError(Optional.empty(), true, 1));
        assertEquals(Optional.of("reset.wrong-place"), StatsRules.resetError(Optional.of(BEDWARS), false, 1));
        StatsMode fixed = mode(5, "cores", Metric.POINTS, Map.of(), List.of(), true);
        StatsMode notResettable = new StatsMode(5, fixed.key(), fixed.i18nKey(), "aqua", null, true, Metric.POINTS,
                Map.of(), List.of(), false, true, "BEACON");
        assertEquals(Optional.of("reset.not-resettable"), StatsRules.resetError(Optional.of(notResettable), true, 1));
        assertEquals(Optional.of("reset.no-token"), StatsRules.resetError(Optional.of(BEDWARS), true, noToken));
        assertEquals(Optional.empty(), StatsRules.resetError(Optional.of(BEDWARS), true, 1));
        assertEquals(Optional.of(true), StatsRules.chatAnswer(" JA "));
        assertEquals(Optional.of(false), StatsRules.chatAnswer("nein"));
        assertEquals(Optional.empty(), StatsRules.chatAnswer("vielleicht"));
    }

    @Test
    void rollierendesFenster() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        assertTrue(RollingWindow.contains(day, day.plusDays(29), 30));
        assertFalse(RollingWindow.contains(day, day.plusDays(30), 30));
        assertEquals(day, RollingWindow.rolloverDay(day.plusDays(30)));
        assertEquals(day, RollingWindow.firstDay(day, 1));
        assertTrue(RollingWindow.contains(day.minusDays(2), day, 3));
        assertFalse(RollingWindow.contains(day.minusDays(3), day, 3));
        assertEquals(List.of(YearMonth.of(2026, 11)), RollingWindow.partitionsToCreate(LocalDate.of(2026, 11, 24)));
        assertEquals(List.of(YearMonth.of(2026, 11), YearMonth.of(2026, 12)),
                RollingWindow.partitionsToCreate(LocalDate.of(2026, 11, 25)));
        assertTrue(RollingWindow.partitionExpired(YearMonth.of(2026, 9), LocalDate.of(2026, 11, 5)));
        assertFalse(RollingWindow.partitionExpired(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 5)));
    }

    @Test
    void berichtNachFeldern() {
        StatLine line = new StatLine(UUID.randomUUID(), BEDWARS.key(), 314, 28, 165, 140, 10_560,
                Map.of("beds", 199L));
        StatsReport report = StatsReport.of(BEDWARS, line, OptionalLong.of(3));
        assertEquals(BEDWARS.fields(), report.entries().stream().map(StatsReport.Entry::field).toList());
        assertEquals(new StatsReport.Value.Count(3), report.entries().getFirst().value());
        assertEquals(new StatsReport.Value.KillDeath(314, 28), report.entries().get(4).value());
        assertEquals(new StatsReport.Value.WinRate(140, 165), report.entries().get(7).value());
        assertEquals(new StatsReport.Value.Count(199), report.entries().get(8).value());
        assertEquals("stats.label.beds", report.entries().get(8).labelKey());
        StatsReport unranked = StatsReport.of(BEDWARS, line, OptionalLong.empty());
        assertEquals(new StatsReport.Value.Missing(), unranked.entries().getFirst().value());
        StatLine built = new StatLine(line.uuid(), MASTERBUILDERS.key(), 0, 0, 7, 2, 30, Map.of());
        StatsReport noRanking = StatsReport.of(MASTERBUILDERS, built, OptionalLong.of(1));
        assertEquals(List.of("games", "wins", "losses", "points"),
                noRanking.entries().stream().map(StatsReport.Entry::field).toList());
        assertEquals(new StatsReport.Value.Count(5), noRanking.entries().get(2).value());
    }

    @Test
    void extraWerteVerdichten() {
        Map<String, Long> merged = StatKeys.merge(Map.of("beds", 2L, "best_time_ms", 90_000L, "level_record", 7L),
                Map.of("beds", 3L, "best_time_ms", 80_000L, "level_record", 5L));
        assertEquals(Map.of("beds", 5L, "best_time_ms", 80_000L, "level_record", 7L), merged);
        assertEquals(Map.of("best_time_ms", 60_000L), StatKeys.merge(Map.of("best_time_ms", 0L),
                Map.of("best_time_ms", 60_000L)));
    }

    @Test
    void gleichstandWechseltAlleFuenfSekunden() {
        List<TopEntry> entries = List.of(entry(1, "A", 50), entry(2, "B", 40), entry(3, "C", 40), entry(4, "D", 40),
                entry(5, "E", 10));
        assertEquals(List.of("A", "B", "C", "D", "E"), names(TieRotation.rotate(entries, 0, 5)));
        assertEquals(List.of("A", "C", "D", "B", "E"), names(TieRotation.rotate(entries, 1, 5)));
        List<TopEntry> cut = TieRotation.rotate(entries, 2, 2);
        assertEquals(List.of("A", "D"), names(cut));
        assertEquals(2, cut.get(1).position());
    }

    static TopEntry entry(int position, String name, double score) {
        return new TopEntry(position, UUID.nameUUIDFromBytes(name.getBytes()), name, "§a", score);
    }

    static List<String> names(List<TopEntry> entries) {
        return entries.stream().map(TopEntry::name).toList();
    }
}
