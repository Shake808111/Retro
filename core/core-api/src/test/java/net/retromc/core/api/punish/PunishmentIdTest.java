package net.retromc.core.api.punish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PunishmentIdTest {

    @Test
    void formateSpec01A5() {
        assertTrue(PunishmentId.newBanId().matches("^[0-9A-Za-z]{11}$"));
        assertTrue(PunishmentId.reportBanId().matches("^R:[0-9A-Z]{8}$"));
        assertTrue(PunishmentId.anticheatBanId().matches("^X:[0-9A-Z]{8}$"));
        assertTrue(PunishmentId.newMuteId().matches("^[0-9A-Z]{8}$"));
        assertTrue(PunishmentId.newReportId().matches("^[0-9A-Z]{8}$"));
    }

    @Test
    void hunderttausendIdsOhneDuplikat() {
        assertUnique(PunishmentId::newBanId);
        assertUnique(PunishmentId::reportBanId);
        assertUnique(PunishmentId::newMuteId);
    }

    private static void assertUnique(Supplier<String> generator) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            assertTrue(seen.add(generator.get()), "Duplikat nach " + i + " IDs");
        }
    }

    @Test
    void idPasstZuArtUndHerkunft() {
        assertTrue(PunishmentId.BAN.matcher(PunishmentId.forPunishment(PunishmentType.BAN, PunishmentOrigin.MANUAL))
                .matches());
        assertTrue(PunishmentId.REPORT_BAN.matcher(
                PunishmentId.forPunishment(PunishmentType.BAN, PunishmentOrigin.REPORT)).matches());
        assertTrue(PunishmentId.ANTICHEAT_BAN.matcher(
                PunishmentId.forPunishment(PunishmentType.BAN, PunishmentOrigin.AC)).matches());
        assertTrue(PunishmentId.MUTE.matcher(PunishmentId.forPunishment(PunishmentType.MUTE, PunishmentOrigin.AC))
                .matches());
        assertEquals(8, PunishmentId.forPunishment(PunishmentType.KICK, PunishmentOrigin.MANUAL).length());
        assertTrue(PunishmentId.isPunishmentId("R:AB12CD34"));
        assertTrue(PunishmentId.isPunishmentId("aZ3kL9mN0pQ"));
        assertFalse(PunishmentId.isPunishmentId("Spieler_1"));
        assertFalse(PunishmentId.isPunishmentId("R:ab12cd34"));
    }
}
