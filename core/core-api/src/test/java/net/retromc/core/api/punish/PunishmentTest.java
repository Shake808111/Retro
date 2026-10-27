package net.retromc.core.api.punish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    private static final Instant ISSUED = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID PLAYER = new UUID(1, 2);
    private static final long DAY = 86_400;

    /** Seed V1_2__seed_reasons.sql (Spec 01 §6.10). */
    private static final PunishmentReason HACKING =
            new PunishmentReason("hacking", PunishmentType.BAN, "punish.reason.hacking",
                    List.of(30 * DAY, 90 * DAY, -1L), true, null);
    private static final PunishmentReason CHAT_RESPECT =
            new PunishmentReason("chat_respect", PunishmentType.MUTE, "punish.reason.chat_respect",
                    List.of(3 * DAY, 7 * DAY, 30 * DAY), true, null);
    private static final PunishmentReason RANDOMKILLING =
            new PunishmentReason("randomkilling", PunishmentType.BAN, "punish.reason.randomkilling",
                    List.of(DAY, 3 * DAY, 7 * DAY), true, "TTT,REPLAY");

    @Test
    void stufenGedeckeltAufLetzteStufe() {
        assertEquals(1, HACKING.stageFor(0));
        assertEquals(2, HACKING.stageFor(1));
        assertEquals(3, HACKING.stageFor(2));
        assertEquals(3, HACKING.stageFor(9));
        assertEquals(Duration.ofDays(30), HACKING.durationForStage(1).duration());
        assertEquals(Duration.ofDays(90), HACKING.durationForStage(2).duration());
        assertTrue(HACKING.durationForStage(3).isPermanent());
        assertTrue(HACKING.durationForStage(7).isPermanent());
        assertEquals(Duration.ofDays(3), CHAT_RESPECT.durationForStage(1).duration());
        assertThrows(IllegalArgumentException.class, () -> HACKING.durationForStage(0));
    }

    @Test
    void meldbarNachServerModus() {
        assertTrue(HACKING.reportableOn("bedwars"));
        assertTrue(RANDOMKILLING.reportableOn("ttt"));
        assertTrue(RANDOMKILLING.reportableOn("replay"));
        assertFalse(RANDOMKILLING.reportableOn("bedwars"));
        assertThrows(IllegalArgumentException.class, () -> new PunishmentReason("x", PunishmentType.KICK, "k",
                List.of(1L), false, null));
        assertThrows(IllegalArgumentException.class, () -> new PunishmentReason("x", PunishmentType.BAN, "k",
                List.of(0L), false, null));
    }

    @Test
    void dauerArgumente() {
        assertEquals(Optional.of(PunishmentDuration.PERMANENT), PunishmentDuration.parse("perm"));
        assertEquals(Optional.of(PunishmentDuration.PERMANENT), PunishmentDuration.parse("Permanent"));
        assertEquals(Optional.of(PunishmentDuration.of(Duration.ofDays(30))), PunishmentDuration.parse("30d"));
        assertEquals(Optional.of(PunishmentDuration.of(Duration.ofHours(12))), PunishmentDuration.parse("12h"));
        assertTrue(PunishmentDuration.parse("0s").isEmpty());
        assertTrue(PunishmentDuration.parse("bald").isEmpty());
        assertEquals(-1, PunishmentDuration.PERMANENT.seconds());
        assertEquals(PunishmentDuration.PERMANENT, PunishmentDuration.ofSeconds(-1));
        assertNull(PunishmentDuration.PERMANENT.expiresAt(ISSUED));
        assertEquals(ISSUED.plusSeconds(60), PunishmentDuration.ofSeconds(60).expiresAt(ISSUED));
    }

    private static Punishment punishment(PunishmentType type, Instant expires, Instant revoked) {
        return new Punishment(1, "aZ3kL9mN0pQ", PLAYER, type, "hacking", null, null, ISSUED, expires, revoked,
                null, null, null, "bedwars-1", PunishmentOrigin.MANUAL, null, 1);
    }

    @Test
    void statusUndRestzeit() {
        Instant now = ISSUED.plus(Duration.ofDays(1));
        Punishment temp = punishment(PunishmentType.BAN, ISSUED.plus(Duration.ofDays(30)), null);
        assertEquals(PunishmentStatus.ACTIVE, temp.status(now));
        assertEquals(Optional.of(Duration.ofDays(29)), temp.remaining(now));
        assertEquals(Duration.ofDays(30), temp.duration().duration());
        assertEquals(PunishmentStatus.EXPIRED, temp.status(ISSUED.plus(Duration.ofDays(30))));
        Punishment perm = punishment(PunishmentType.BAN, null, null);
        assertTrue(perm.isActive(now));
        assertEquals(Optional.empty(), perm.remaining(now));
        assertTrue(perm.duration().isPermanent());
        assertEquals(PunishmentStatus.REVOKED, punishment(PunishmentType.BAN, null, now).status(now));
        assertEquals(PunishmentStatus.EXPIRED, punishment(PunishmentType.KICK, null, null).status(now));
        assertThrows(IllegalArgumentException.class,
                () -> punishment(PunishmentType.BAN, ISSUED.minusSeconds(1), null));
    }

    @Test
    void antraege() {
        PunishmentRequest ac = PunishmentRequest.anticheat(PLAYER, "clientmod", 3574, "bedwars-1");
        assertEquals(PunishmentOrigin.AC, ac.origin());
        assertEquals(3574, ac.acCode());
        assertThrows(IllegalArgumentException.class, () -> new PunishmentRequest(PLAYER, "hacking", null, null,
                PunishmentOrigin.MANUAL, 1, null, null, null, null));
        PunishmentRequest manual = PunishmentRequest.manual(PLAYER, "hacking", new UUID(9, 9),
                PunishmentDuration.PERMANENT, null);
        assertTrue(manual.durationOverride().isPermanent());
    }
}
