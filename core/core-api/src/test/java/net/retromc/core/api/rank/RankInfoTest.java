package net.retromc.core.api.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RankInfoTest {

    @Test
    void zeitrangMitRestlaufzeit() {
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        RankInfo base = RankInfo.of(Rank.PREMIUM);
        assertTrue(base.isPermanent());
        assertEquals(Optional.empty(), base.remaining(now));
        RankInfo timed = new RankInfo(base.id(), base.displayName(), base.prefix(), base.legacyColor(),
                base.gradient(), base.weight(), base.limits(), base.flags(), now.plus(Duration.ofDays(3)));
        assertFalse(timed.isPermanent());
        assertEquals(Optional.of(Duration.ofDays(3)), timed.remaining(now));
        assertEquals(Optional.of(Duration.ZERO), timed.remaining(now.plus(Duration.ofDays(4))));
        assertTrue(timed.isAtLeast(200));
        assertFalse(timed.isTeam());
    }

    @Test
    void anzeigeUndNick() {
        RankDisplay vip = RankDisplay.of(Rank.VIP);
        assertEquals("§d", vip.nameColor(false));
        assertEquals("§5", vip.nameColor(true));
        assertTrue(vip.coloredGg());
        assertFalse(vip.booster());
        RankDisplay nicked = RankDisplay.nicked();
        assertEquals("premium", nicked.group());
        assertEquals("§6", nicked.color());
        assertEquals(" §8┃ ", RankDisplay.separator(true));
        assertEquals(" §7| ", RankDisplay.separator(false));
        assertEquals("#FF55FF:#55FFFF", RankDisplay.of(Rank.SUPREMIUM_PLUS).gradient());
    }
}
