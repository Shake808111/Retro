package net.retromc.core.api.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemplateIdTest {

    @Test
    void servernamenSpec08Abschnitt66() {
        TemplateId template = TemplateId.of("bedwars-4x4");
        assertEquals("bedwars-4x4-12", template.serverName(12));
        assertEquals(OptionalInt.of(12), template.numberOf("bedwars-4x4-12"));
        assertEquals(OptionalInt.empty(), template.numberOf("bedwars-8x1-3"));
        assertEquals(OptionalInt.empty(), TemplateId.LOBBY.numberOf("lobby"));
        assertEquals(OptionalInt.of(3), TemplateId.trailingNumber("lobby-3"));
        assertEquals("lobby-bedwars", TemplateId.modeLobby("bedwars").value());
        assertThrows(IllegalArgumentException.class, () -> template.serverName(0));
    }

    @Test
    void kleinsteFreieNummerNameAllocatorTest() {
        // Spec 08 §9 Nr. 6: Laufen x-1 und x-3, heißt der nächste Server x-2.
        assertEquals(2, TemplateId.lowestFreeNumber(Set.of(1, 3)));
        assertEquals(1, TemplateId.lowestFreeNumber(Set.of()));
        assertEquals(4, TemplateId.lowestFreeNumber(List.of(1, 2, 3)));
    }

    @Test
    void ungueltigeTemplateIds() {
        assertThrows(IllegalArgumentException.class, () -> TemplateId.of("a"));
        assertThrows(IllegalArgumentException.class, () -> TemplateId.of("Lobby"));
        assertThrows(IllegalArgumentException.class, () -> TemplateId.of("x".repeat(25)));
        assertThrows(IllegalArgumentException.class, () -> TemplateId.of("bed_wars"));
    }

    @Test
    void registryEreignisse() {
        ServerInfo before = ServerInfoTest.game(ServerState.WAITING, 1, 8, 0, false);
        ServerInfo after = ServerInfoTest.game(ServerState.STARTING, 2, 8, 0, false);
        ServerEvent.Updated updated = new ServerEvent.Updated(before, after);
        assertEquals("bedwars-4x4-12", updated.server());
        assertEquals(true, updated.stateChanged());
        assertEquals("bedwars-4x4-12", new ServerEvent.Registered(after).server());
        assertEquals("x-1", new ServerEvent.Unregistered("x-1", "HEARTBEAT_LOST").server());
        assertThrows(IllegalArgumentException.class, () -> new ProxyInfo("proxy-1", -1, Instant.EPOCH, "b"));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceState(true, null, null));
        assertEquals(false, MaintenanceState.OFF.enabled());
    }
}
