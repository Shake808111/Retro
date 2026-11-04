package net.retromc.game.framework.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankLimits;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.map.ForcemapPolicy.Outcome;
import net.retromc.game.framework.map.ForcemapPolicy.Request;
import org.junit.jupiter.api.Test;

/** Spec 03 §9 #12: Limits, Tageswechsel 00:00 Europe/Berlin, 1× pro Runde, gesperrt ≤ 10 s. */
class ForcemapPolicyTest {

    private static Request request(int limit, int used, int rest, boolean already) {
        return new Request(GameState.STARTING, rest, true, true, false, already, limit, used, false);
    }

    @Test
    void limitsJeRang() {
        int supremium = Rank.SUPREMIUM.defaultLimits().forcemapPerDay();
        int supremiumPlus = Rank.SUPREMIUM_PLUS.defaultLimits().forcemapPerDay();
        assertEquals(10, supremium);
        assertEquals(30, supremiumPlus);
        assertEquals(RankLimits.UNLIMITED, Rank.VIP.defaultLimits().forcemapPerDay());
        assertEquals(Outcome.OK, ForcemapPolicy.check(request(supremium, 9, 30, false)));
        assertEquals(Outcome.LIMIT, ForcemapPolicy.check(request(supremium, 10, 30, false)));
        assertEquals(Outcome.OK, ForcemapPolicy.check(request(supremiumPlus, 29, 30, false)));
        assertEquals(Outcome.LIMIT, ForcemapPolicy.check(request(supremiumPlus, 30, 30, false)));
        assertEquals(Outcome.OK, ForcemapPolicy.check(request(RankLimits.UNLIMITED, 500, 30, false)));
        assertEquals(Outcome.NO_PERMISSION, ForcemapPolicy.check(request(
                Rank.PREMIUM_PLUS.defaultLimits().forcemapPerDay(), 0, 30, false)));
    }

    @Test
    void einmalProRundeUndSperreAbZehn() {
        assertEquals(Outcome.ALREADY, ForcemapPolicy.check(request(10, 0, 30, true)));
        assertEquals(Outcome.OK, ForcemapPolicy.check(request(10, 0, 11, false)));
        assertEquals(Outcome.TOO_LATE, ForcemapPolicy.check(request(10, 0, 10, false)));
        assertEquals(Outcome.TOO_LATE, ForcemapPolicy.check(new Request(GameState.INGAME, 60, true, true, false,
                false, 10, 0, false)));
        assertEquals(Outcome.OK, ForcemapPolicy.check(new Request(GameState.WAITING, 60, true, true, false, false,
                10, 0, false)));
    }

    @Test
    void unbekannteGleicheUndBesitzer() {
        assertEquals(Outcome.UNKNOWN, ForcemapPolicy.check(new Request(GameState.WAITING, 60, false, true, false,
                false, 10, 0, false)));
        assertEquals(Outcome.UNKNOWN, ForcemapPolicy.check(new Request(GameState.WAITING, 60, true, false, false,
                false, 10, 0, false)));
        assertEquals(Outcome.SAME, ForcemapPolicy.check(new Request(GameState.WAITING, 60, true, true, true, false,
                10, 0, false)));
        assertEquals(Outcome.OK, ForcemapPolicy.check(new Request(GameState.WAITING, 60, true, true, false, true,
                0, 99, true)), "Besitzer/Admin ohne Recht, Limit und Rundenregel");
        assertEquals("common.forcemap.limit", Outcome.LIMIT.key());
    }

    @Test
    void tageswechselEuropeBerlin() {
        assertEquals(LocalDate.of(2026, 11, 3), ForcemapPolicy.day(Instant.parse("2026-11-03T22:59:59Z")));
        assertEquals(LocalDate.of(2026, 11, 4), ForcemapPolicy.day(Instant.parse("2026-11-03T23:00:00Z")));
        assertEquals(LocalDate.of(2026, 7, 2), ForcemapPolicy.day(Instant.parse("2026-07-01T22:00:00Z")),
                "Sommerzeit: 00:00 MESZ = 22:00 UTC");
        UUID player = new UUID(1, 2);
        assertEquals("cnt:forcemap:" + player + ":2026-11-04",
                ForcemapPolicy.counterKey(player, LocalDate.of(2026, 11, 4)));
        assertEquals(26, ForcemapPolicy.COUNTER_TTL.toHours());
    }
}
