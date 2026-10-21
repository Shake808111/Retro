package net.retromc.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.social.FriendPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreApiTest {

    @Test
    void brandIdIstGueltigeVelocityId() {
        // Velocity-Plugin-IDs: [a-z][a-z0-9-_]{0,63}; wir hängen später "-core" an.
        assertTrue(BuildInfo.BRAND_ID.matches("[a-z][a-z0-9]{0,58}"), BuildInfo.BRAND_ID);
        assertFalse(BuildInfo.BRAND_NAME.isBlank());
        assertFalse(BuildInfo.VERSION.isBlank());
    }

    @Test
    void freundespaarFolgtPostgresReihenfolge() {
        // Höchstes Bit gesetzt: für UUID.compareTo "negativ", für PostgreSQL aber größer.
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(high.compareTo(low) < 0, "Voraussetzung: Java vergleicht mit Vorzeichen");

        FriendPair pair = FriendPair.of(high, low);
        assertEquals(low, pair.a());
        assertEquals(high, pair.b());
        assertEquals(pair, FriendPair.of(low, high));
        assertThrows(IllegalArgumentException.class, () -> FriendPair.of(low, low));
        assertThrows(IllegalArgumentException.class, () -> new FriendPair(high, low));
    }

    @Test
    void serverInfoFreiePlaetze() {
        ServerInfo info = new ServerInfo("bedwars-1", "bedwars", "test", "testmap", ServerState.WAITING, 3, 8);
        assertEquals(5, info.freeSlots());
        assertTrue(info.state().acceptsPlayers());
        assertFalse(ServerState.INGAME.acceptsPlayers());
    }
}
