package net.retromc.core.data.stats;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisFuture;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.retromc.core.api.event.LeaderboardChanged;
import net.retromc.core.api.event.StatsUpdated;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.PlayerMatchStats;
import net.retromc.core.api.stats.ResetResult;
import net.retromc.core.api.stats.ResetScope;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.SubmitResult;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.api.util.NetworkTime;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.data.social.DataFixture;
import net.retromc.core.data.social.PlayerRepository;
import net.retromc.core.data.testing.TestDatabases;
import net.retromc.core.data.testing.TestTransactions;
import net.retromc.core.stats.RankingScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Abnahme Spec 02 §9 #7–#10, #21, #22 gegen PostgreSQL 18.6 und Valkey 8.1.10 (Uhr injiziert). */
class StatsRepositoryIT {

    static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    DataFixture fixture;
    StatsRepository stats;
    DataSource dataSource;

    @BeforeEach
    void setUp() {
        TestDatabases.flushValkey();
        fixture = new DataFixture();
        dataSource = fixture.database.dataSource();
        stats = new StatsRepository(fixture.async, fixture.valkey, () -> fixture.ranks, fixture.events, fixture.clock);
        at(DAY);
        await(stats.housekeeping(DAY));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    void at(LocalDate day) {
        fixture.clock.set(day.atTime(LocalTime.NOON).atZone(NetworkTime.ZONE).toInstant());
    }

    static MatchResult match(ModeKey mode, PlayerMatchStats... players) {
        Instant end = Instant.parse("2026-11-02T11:00:00Z");
        return new MatchResult(UUID.randomUUID(), mode, "4x4", "Farmland", "bedwars-4x4-1", end.minusSeconds(900), end,
                1, List.of(players));
    }

    static PlayerMatchStats bedwars(UUID player, int kills, int deaths, int beds, boolean win) {
        return new PlayerMatchStats(player, 1, win ? 1 : 2, kills, deaths, win, win, 10, null, Map.of("beds",
                (long) beds));
    }

    double score(ModeKey mode, StatPeriod period, UUID player) {
        Double score = valkey(fixture.valkey.zscore(ValkeyKeys.leaderboard(mode, period), player.toString()));
        return score == null ? Double.NaN : score;
    }

    @Test
    void abnahme8GleicheMatchIdZaehltNurEinmal() {
        UUID player = fixture.player("Alpha");
        MatchResult result = match(ModeKey.BEDWARS, bedwars(player, 3, 1, 1, true));
        SubmitResult first = await(stats.submit(result));
        SubmitResult second = await(stats.submit(result));
        assertTrue(first.applied());
        assertEquals(15 + 10 + 50, first.deltas().get(player).points());
        assertFalse(second.applied());
        StatLine all = await(stats.line(player, ModeKey.BEDWARS, StatWindow.ALL));
        assertEquals(new StatLine(player, ModeKey.BEDWARS, 3, 1, 1, 1, 75, Map.of("beds", 1L)), all);
        assertEquals(RankingScore.points(75, 1), score(ModeKey.BEDWARS, StatPeriod.ALL, player));
        assertEquals(RankingScore.points(75, 1), score(ModeKey.BEDWARS, StatPeriod.DAYS_30, player));
        assertEquals(1, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.match_player"));
        assertEquals(1, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.match WHERE applied_at"
                + " IS NOT NULL"));
        assertEquals(List.of(new StatsUpdated(ModeKey.BEDWARS, List.of(player))),
                fixture.events.of(StatsUpdated.class));
        Map<String, StatLine> maps = await(stats.mapLines(player, ModeKey.BEDWARS));
        assertEquals(75, maps.get("Farmland").points());
    }

    @Test
    void abnahme7RollierendesFensterMitRolloverUmMitternacht() {
        UUID early = fixture.player("Early");
        UUID later = fixture.player("Later");
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(early, 3, 0, 1, true))));
        at(DAY.plusDays(1));
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(later, 2, 0, 0, false), bedwars(early, 1, 0, 0, false))));

        LocalDate lastDay = DAY.plusDays(29);
        at(lastDay);
        await(stats.housekeeping(lastDay));
        assertTrue(await(stats.rollover(lastDay)));
        assertEquals(75 + 5, await(stats.line(early, ModeKey.BEDWARS, StatWindow.THIRTY_DAYS)).points(),
                "Runde an D zählt bis einschließlich D+29");
        assertEquals(RankingScore.points(80, 1), score(ModeKey.BEDWARS, StatPeriod.DAYS_30, early));

        LocalDate dropDay = DAY.plusDays(30);
        at(dropDay);
        await(stats.housekeeping(dropDay));
        assertTrue(await(stats.rollover(dropDay)));
        assertFalse(await(stats.rollover(dropDay)), "nur ein Rollover pro Tag im Netzwerk");
        StatLine window = await(stats.line(early, ModeKey.BEDWARS, StatWindow.THIRTY_DAYS));
        assertEquals(5, window.points());
        assertEquals(1, window.games());
        assertEquals(RankingScore.points(window.points(), window.wins()),
                score(ModeKey.BEDWARS, StatPeriod.DAYS_30, early), "ZSET-Score = SQL-Summe");
        assertEquals(RankingScore.points(10, 0), score(ModeKey.BEDWARS, StatPeriod.DAYS_30, later));
        assertEquals(RankingScore.points(80, 1), score(ModeKey.BEDWARS, StatPeriod.ALL, early));
        assertEquals(80, await(stats.line(early, ModeKey.BEDWARS, StatWindow.ALL)).points());

        at(DAY.plusDays(31));
        assertTrue(await(stats.rollover(DAY.plusDays(31))));
        assertTrue(Double.isNaN(score(ModeKey.BEDWARS, StatPeriod.DAYS_30, early)), "Score 0 fällt aus der Liste");
        assertEquals(OptionalLong.empty(), await(stats.position(early, ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        assertTrue(fixture.events.of(LeaderboardChanged.class).contains(new LeaderboardChanged(ModeKey.BEDWARS,
                StatPeriod.DAYS_30)));
    }

    @Test
    void statsdSummiertDieLetztenTage() {
        UUID player = fixture.player("Days");
        await(stats.submit(match(ModeKey.SKYWARS, skywars(player, 4, true))));
        at(DAY.plusDays(2));
        await(stats.submit(match(ModeKey.SKYWARS, skywars(player, 1, false))));
        assertEquals(1, await(stats.line(player, ModeKey.SKYWARS, StatWindow.lastDays(1))).kills());
        assertEquals(1, await(stats.line(player, ModeKey.SKYWARS, StatWindow.lastDays(2))).kills());
        assertEquals(5, await(stats.line(player, ModeKey.SKYWARS, StatWindow.lastDays(3))).kills());
        assertEquals(4 + 5 + 1, await(stats.line(player, ModeKey.SKYWARS, StatWindow.THIRTY_DAYS)).points());
    }

    static PlayerMatchStats skywars(UUID player, int kills, boolean win) {
        return new PlayerMatchStats(player, null, null, kills, win ? 0 : 1, win, win, 0, null, Map.of());
    }

    @Test
    void abnahme9ResetVerbrauchtTokenNurBeiErfolg() {
        UUID player = fixture.player("Resetter");
        TestTransactions.committed(dataSource, connection -> Jdbc.update(connection,
                "INSERT INTO economy.wallet (uuid, coins, reset_tokens) VALUES (?, 100, 1)", player));
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(player, 3, 1, 1, true))));

        // Abbruch: Dialog öffnen und wieder wegnehmen kostet nichts
        assertTrue(await(stats.openReset(player, ModeKey.BEDWARS)));
        assertFalse(await(stats.openReset(player, ModeKey.BEDWARS)), "kein zweiter offener Dialog");
        assertEquals(Optional.of(ModeKey.BEDWARS), await(stats.takeReset(player)));
        assertEquals(Optional.empty(), await(stats.takeReset(player)));
        // Ablauf (30 s, hier verkürzt): danach ist nichts mehr offen
        assertTrue(await(stats.openReset(player, ModeKey.BEDWARS)));
        valkey(fixture.valkey.pexpire(ValkeyKeys.resetConfirm(player), 1));
        sleep(Duration.ofMillis(50));
        assertEquals(Optional.empty(), await(stats.takeReset(player)));
        assertEquals(1, tokens(player));

        assertEquals(ResetResult.DONE, await(stats.reset(player, ModeKey.BEDWARS, ResetScope.PLAYER, null)));
        assertEquals(0, tokens(player));
        assertEquals(0, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.daily WHERE uuid = ?", player));
        assertEquals(0, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.alltime WHERE uuid = ?",
                player));
        assertEquals(0, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.map_alltime WHERE uuid = ?",
                player));
        assertTrue(Double.isNaN(score(ModeKey.BEDWARS, StatPeriod.DAYS_30, player)));
        assertTrue(Double.isNaN(score(ModeKey.BEDWARS, StatPeriod.ALL, player)));
        assertEquals(75, TestTransactions.count(dataSource, "SELECT (snapshot -> 'alltime' ->> 'points')::bigint"
                + " FROM stats.reset_log WHERE uuid = ? AND scope = 'player' AND ledger_id IS NOT NULL", player));
        assertEquals(1, TestTransactions.count(dataSource, "SELECT count(*) FROM economy.ledger WHERE uuid = ?"
                + " AND currency = 'RESET_TOKEN' AND delta = -1 AND reason = 'STATS_RESET'", player));
        assertEquals(100, TestTransactions.count(dataSource, "SELECT coins FROM economy.wallet WHERE uuid = ?",
                player), "kein Coin-Reset (O11)");
        assertEquals(StatLine.empty(player, ModeKey.BEDWARS), await(stats.line(player, ModeKey.BEDWARS,
                StatWindow.ALL)));

        assertEquals(ResetResult.NO_TOKEN, await(stats.reset(player, ModeKey.BEDWARS, ResetScope.PLAYER, null)));
        assertEquals(ResetResult.NOT_RESETTABLE, await(stats.reset(player, ModeKey.CORES, ResetScope.PLAYER, null)));
        assertEquals(ResetResult.UNKNOWN_MODE, await(stats.reset(player, ModeKey.of("gibtsnicht"),
                ResetScope.PLAYER, null)));
        UUID staff = fixture.player("Staff");
        assertEquals(ResetResult.DONE, await(stats.reset(player, ModeKey.CORES, ResetScope.STAFF, staff)));
        assertEquals(1, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.reset_log WHERE scope = 'staff'"
                + " AND actor = ? AND ledger_id IS NULL", staff));
    }

    long tokens(UUID player) {
        return TestTransactions.count(dataSource, "SELECT reset_tokens FROM economy.wallet WHERE uuid = ?", player);
    }

    @Test
    void abnahme10RankingMitSiegenUndTop() {
        UUID fewer = fixture.player("Fewer");
        UUID more = fixture.player("More");
        UUID outside = fixture.player("Outside");
        fixture.ranks.set(more, Rank.PREMIUM);
        // gleiche Punkte (60): 1 Sieg + 2 Kills gegen 0 Siege + 12 Kills
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(more, 2, 0, 0, true), bedwars(fewer, 12, 0, 0, false))));
        assertEquals(OptionalLong.of(1), await(stats.position(more, ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        assertEquals(OptionalLong.of(2), await(stats.position(fewer, ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        assertEquals(OptionalLong.empty(), await(stats.position(outside, ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        assertEquals(Optional.of(more), await(stats.byRank(ModeKey.BEDWARS, StatPeriod.DAYS_30, 1)));
        assertEquals(Optional.empty(), await(stats.byRank(ModeKey.BEDWARS, StatPeriod.DAYS_30, 3)));
        List<TopEntry> top = await(stats.top(ModeKey.BEDWARS, StatPeriod.ALL, 10));
        assertEquals(List.of("More", "Fewer"), top.stream().map(TopEntry::name).toList());
        assertEquals("§6", top.getFirst().color());
        assertEquals(60, RankingScore.pointsOf(top.getFirst().score()));
        assertEquals(1, RankingScore.winsOf(top.getFirst().score()));
        assertTrue(valkey(fixture.valkey.exists(ValkeyKeys.topCache(ModeKey.BEDWARS, StatPeriod.ALL))) > 0);
        assertEquals(top.subList(0, 1), await(stats.top(ModeKey.BEDWARS, StatPeriod.ALL, 1)));
        // Ein neues Rundenende leert den Top-Cache
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(outside, 1, 0, 0, false))));
        assertEquals(0, valkey(fixture.valkey.exists(ValkeyKeys.topCache(ModeKey.BEDWARS, StatPeriod.ALL))));
    }

    @Test
    void neuaufbauUndReconcileStellenSqlStandHer() {
        UUID player = fixture.player("Rebuilt");
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(player, 3, 1, 1, true))));
        valkey(fixture.valkey.del(ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.ALL),
                ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        valkey(fixture.valkey.zadd(ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.DAYS_30), 999,
                UUID.randomUUID().toString()));
        assertEquals(1L, await(stats.rebuild(ModeKey.BEDWARS)));
        assertEquals(RankingScore.points(75, 1), score(ModeKey.BEDWARS, StatPeriod.ALL, player));
        assertEquals(1L, valkey(fixture.valkey.zcard(ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.DAYS_30))));
        assertTrue(await(stats.reconcile(DAY)));
        assertFalse(await(stats.reconcile(DAY)));
        assertEquals(RankingScore.points(75, 1), score(ModeKey.BEDWARS, StatPeriod.DAYS_30, player));
        assertEquals(0L, await(stats.rebuild(ModeKey.MASTERBUILDERS)));
    }

    @Test
    void bestzeitUndKarma() {
        UUID runner = fixture.player("Runner");
        await(stats.submit(match(ModeKey.BINGO_X1, timed(runner, 90_000))));
        await(stats.submit(match(ModeKey.BINGO_X1, timed(runner, 80_000))));
        await(stats.submit(match(ModeKey.BINGO_X1, timed(runner, 100_000))));
        assertEquals(-80_000, score(ModeKey.BINGO_X1, StatPeriod.ALL, runner));
        assertEquals(-80_000, score(ModeKey.BINGO_X1, StatPeriod.DAYS_30, runner));
        StatLine all = await(stats.line(runner, ModeKey.BINGO_X1, StatWindow.ALL));
        assertEquals(80_000, all.extra().get("best_time_ms"));
        assertEquals(3, all.games());

        UUID detective = fixture.player("Detective");
        await(stats.submit(match(ModeKey.TTT, new PlayerMatchStats(detective, null, null, 2, 0, true, true, 0, null,
                Map.of("karma", 40L)))));
        assertEquals(40, score(ModeKey.TTT, StatPeriod.ALL, detective));
        assertEquals(40, await(stats.line(detective, ModeKey.TTT, StatWindow.THIRTY_DAYS)).value("karma")
                .orElseThrow());
    }

    static PlayerMatchStats timed(UUID player, long millis) {
        return new PlayerMatchStats(player, null, null, 0, 0, true, true, 0, null, Map.of("best_time_ms", millis));
    }

    @Test
    void togglestatsZustand() {
        UUID player = fixture.player("Hidden");
        assertFalse(await(stats.hidden(player)));
        assertTrue(await(stats.setHidden(player, true)));
        assertTrue(await(stats.hidden(player)));
        assertEquals(1, TestTransactions.count(dataSource, "SELECT count(*) FROM stats.player_settings WHERE uuid = ?"
                + " AND hidden_since IS NOT NULL", player));
        assertFalse(await(stats.setHidden(player, false)));
        assertFalse(await(stats.hidden(player)));
    }

    @Test
    void abnahme11VersteckteSpielerBleibenInBestenlisten() {
        UUID hidden = fixture.player("Versteckt");
        await(stats.submit(match(ModeKey.BEDWARS, bedwars(hidden, 3, 0, 1, true))));
        await(stats.setHidden(hidden, true));
        assertEquals(OptionalLong.of(1), await(stats.position(hidden, ModeKey.BEDWARS, StatPeriod.DAYS_30)));
        assertEquals(List.of("Versteckt"), await(stats.top(ModeKey.BEDWARS, StatPeriod.DAYS_30, 10)).stream()
                .map(TopEntry::name).toList());
    }

    @Test
    void abnahme21NurEineAbfrageProSekunde() {
        UUID player = fixture.player("Spammer");
        assertTrue(await(stats.tryQuery(player)));
        assertFalse(await(stats.tryQuery(player)));
        sleep(Duration.ofMillis(1_100));
        assertTrue(await(stats.tryQuery(player)));
    }

    @Test
    void abnahme22NicknameFuehrtZuKeinemSpieler() {
        UUID real = fixture.player("Echtname");
        valkey(fixture.valkey.hset(ValkeyKeys.nick(real), Map.of("name", "Tarnname", "texture", "t",
                "signature", "s", "since", "0")));
        valkey(fixture.valkey.set(ValkeyKeys.nickName("Tarnname"), real.toString()));
        PlayerRepository profiles = new PlayerRepository(fixture.async, fixture.valkey, () -> fixture.ranks,
                fixture.events);
        assertEquals(Optional.empty(), await(profiles.uuidOf("Tarnname")));
        assertEquals(Optional.of(real), await(profiles.uuidOf("echtname")));
    }

    @Test
    void housekeepingLegtPartitionenAnUndLoeschtAlte() {
        TestTransactions.committed(dataSource, connection -> Jdbc.one(connection,
                "SELECT stats.create_daily_partition(?)", row -> row.getString(1), LocalDate.of(2026, 8, 1)));
        await(stats.housekeeping(LocalDate.of(2026, 11, 25)));
        assertFalse(TestTransactions.exists(dataSource, "stats.daily_202608"));
        assertTrue(TestTransactions.exists(dataSource, "stats.daily_202611"));
        assertTrue(TestTransactions.exists(dataSource, "stats.daily_202612"));
        assertEquals(Optional.empty(), StatsRepository.partitionMonth("daily_default"));
    }

    @Test
    void katalogMitIcon() {
        List<StatsMode> modes = await(stats.modes());
        assertEquals(15, modes.size());
        StatsMode bedwars = await(stats.mode(ModeKey.BEDWARS)).orElseThrow();
        assertEquals(Map.of("kills", 5L, "beds", 10L, "wins", 50L), bedwars.formula());
        assertEquals("RED_BED", bedwars.icon());
        assertEquals(StatsMode.Metric.NONE, await(stats.mode(ModeKey.MASTERBUILDERS)).orElseThrow().metric());
        assertEquals("#D95D51:#ECE12A", await(stats.mode(ModeKey.JUMPLEAGUE)).orElseThrow().gradient());
        assertNull(await(stats.mode(ModeKey.BEDWARS)).orElseThrow().gradient());
    }

    static <T> T valkey(RedisFuture<T> future) {
        return await(future.toCompletableFuture());
    }

    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
