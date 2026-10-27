package net.retromc.core.api.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ServerStateTest {

    @Test
    void zustaendeSpec08Abschnitt61() {
        assertEquals(8, ServerState.values().length);
        assertEquals(EnumSet.of(ServerState.ONLINE, ServerState.WAITING, ServerState.STARTING),
                EnumSet.copyOf(Arrays.stream(ServerState.values()).filter(ServerState::acceptsPlayers)
                        .toList()));
    }

    @Test
    void uebergaenge() {
        assertTrue(ServerState.BOOTING.canTransitionTo(ServerState.ONLINE));
        assertTrue(ServerState.BOOTING.canTransitionTo(ServerState.WAITING));
        assertTrue(ServerState.WAITING.canTransitionTo(ServerState.STARTING));
        assertTrue(ServerState.STARTING.canTransitionTo(ServerState.WAITING), "zu wenige Spieler");
        assertTrue(ServerState.STARTING.canTransitionTo(ServerState.INGAME));
        assertTrue(ServerState.INGAME.canTransitionTo(ServerState.ENDING));
        assertTrue(ServerState.ONLINE.canTransitionTo(ServerState.DRAINING));
        assertFalse(ServerState.INGAME.canTransitionTo(ServerState.WAITING));
        assertFalse(ServerState.ENDING.canTransitionTo(ServerState.WAITING), "eine Runde pro Server");
        assertFalse(ServerState.ONLINE.canTransitionTo(ServerState.WAITING));
        for (ServerState state : ServerState.values()) {
            assertEquals(state != ServerState.STOPPING, state.canTransitionTo(ServerState.STOPPING), state.name());
        }
        assertTrue(ServerState.STOPPING.successors().isEmpty());
    }

    @Test
    void zustandsfarbenSpec08Abschnitt51() {
        assertEquals("§a", ServerState.ONLINE.color());
        assertEquals("§a", ServerState.WAITING.color());
        assertEquals("§e", ServerState.STARTING.color());
        assertEquals("§c", ServerState.INGAME.color());
        assertEquals("§6", ServerState.ENDING.color());
        assertEquals("§6", ServerState.DRAINING.color());
        assertEquals("§7", ServerState.BOOTING.color());
        assertEquals("§8", ServerState.STOPPING.color());
    }
}
