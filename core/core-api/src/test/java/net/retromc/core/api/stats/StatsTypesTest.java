package net.retromc.core.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatsTypesTest {

    private static final UUID PLAYER = new UUID(3, 4);
    private static final Instant START = Instant.parse("2026-10-27T18:00:00Z");

    @Test
    void modusSchluessel() {
        assertEquals("bingo_x2", ModeKey.BINGO_X2.key());
        assertEquals("bedwars", ModeKey.of("bedwars").toString());
        assertThrows(IllegalArgumentException.class, () -> ModeKey.of("BedWars"));
        assertThrows(IllegalArgumentException.class, () -> ModeKey.of("a:b"));
        assertThrows(IllegalArgumentException.class, () -> ModeKey.of("x".repeat(25)));
    }

    @Test
    void zeitraeume() {
        assertEquals("30d", StatPeriod.DAYS_30.key());
        assertEquals("all", StatPeriod.ALL.key());
        assertEquals(30, StatPeriod.DAYS_30.days());
        assertEquals(Optional.of(StatPeriod.ALL), StatPeriod.byKey("all"));
        assertEquals(Optional.empty(), StatPeriod.byKey("monthly"));
    }

    @Test
    void zaehlerzeileSummiertTagesbuckets() {
        StatLine day1 = new StatLine(PLAYER, ModeKey.BEDWARS, 10, 4, 3, 2, 170, Map.of("beds", 5L));
        StatLine day2 = new StatLine(PLAYER, ModeKey.BEDWARS, 2, 1, 1, 0, 10, Map.of("beds", 1L, "x", 7L));
        StatLine sum = day1.plus(day2);
        assertEquals(12, sum.kills());
        assertEquals(180, sum.points());
        assertEquals(Map.of("beds", 6L, "x", 7L), sum.extra());
        assertEquals(OptionalLong.of(6), sum.value("beds"));
        assertEquals(OptionalLong.of(2), sum.value(StatLine.LOSSES));
        assertEquals(OptionalLong.empty(), sum.value("karma"));
        assertEquals(StatLine.empty(PLAYER, ModeKey.BEDWARS), StatLine.empty(PLAYER, ModeKey.BEDWARS));
        assertThrows(IllegalArgumentException.class,
                () -> day1.plus(StatLine.empty(PLAYER, ModeKey.SKYWARS)));
    }

    @Test
    void rundenergebnis() {
        PlayerMatchStats winner = new PlayerMatchStats(PLAYER, 1, 1, 3, 1, true, true, 120, null, Map.of("beds", 1L));
        PlayerMatchStats loser = new PlayerMatchStats(new UUID(5, 6), 2, 2, 1, 1, false, false, 40, null, Map.of());
        MatchResult result = new MatchResult(UUID.randomUUID(), ModeKey.BEDWARS, "4x4", "farmland", "bedwars-4x4-1",
                START, START.plusSeconds(900), 1, List.of(winner, loser));
        assertEquals(1, winner.wins());
        assertEquals(0, loser.wins());
        assertEquals(Optional.of(winner), result.player(PLAYER));
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(UUID.randomUUID(), ModeKey.BEDWARS,
                "4x4", "farmland", "s", START, START.minusSeconds(1), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(UUID.randomUUID(), ModeKey.BEDWARS,
                "4x4", "farmland", "s", START, START, null, List.of(winner, winner)));
    }

    @Test
    void coinAuszahlung() {
        CoinPayout payout = new CoinPayout(PLAYER, 475, 142, 3, List.of(new UUID(7, 7), new UUID(8, 8)));
        assertEquals(617, payout.total());
        assertTrue(payout.boosters().size() < payout.boosterCount() + 1);
        assertThrows(IllegalArgumentException.class, () -> new CoinPayout(PLAYER, 1, 0, 1, List.of(PLAYER)));
    }
}
