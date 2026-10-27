package net.retromc.core.api.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerInfoTest {

    private static final Instant STARTED = Instant.parse("2026-10-27T09:00:00.250Z");

    static ServerInfo game(ServerState state, int online, int max, int reserved, boolean privateRound) {
        return new ServerInfo("bedwars-4x4-12", "bedwars-4x4", "bedwars", "4x4", ServerCategory.GAME, state,
                online, max, reserved, "farmland", "net-bedwars-4x4-12", 25565, 0, false, 0, privateRound,
                "BedWars", "1.21.11+", "m-1", STARTED);
    }

    static ServerInfo lobby(String name, String template, String displayName, ServerState state, int online) {
        return new ServerInfo(name, template, "lobby", ServerInfo.DEFAULT_VARIANT, ServerCategory.LOBBY, state,
                online, 100, 0, null, "net-" + name, 25565, 0, false, 0, false, displayName, null, null, STARTED);
    }

    @Test
    void freiePlaetzeZiehenReservierungenAb() {
        assertEquals(3, game(ServerState.WAITING, 3, 8, 2, false).freeSlots());
        assertEquals(0, game(ServerState.WAITING, 7, 8, 4, false).freeSlots());
    }

    @Test
    void beitretbarNachSpec08Abschnitt67() {
        assertTrue(game(ServerState.WAITING, 3, 8, 2, false).joinable());
        assertTrue(game(ServerState.STARTING, 3, 8, 4, false).joinable());
        assertFalse(game(ServerState.STARTING, 4, 8, 4, false).joinable(), "online + reserved = max");
        assertFalse(game(ServerState.INGAME, 1, 8, 0, false).joinable());
        assertFalse(game(ServerState.WAITING, 1, 8, 0, true).joinable(), "private Runde");
        assertTrue(lobby("lobby-1", "lobby", "Lobby", ServerState.ONLINE, 99).joinable());
        assertFalse(lobby("lobby-1", "lobby", "Lobby", ServerState.ONLINE, 100).joinable());
        assertFalse(lobby("lobby-1", "lobby", "Lobby", ServerState.DRAINING, 10).joinable());
        assertFalse(lobby("lobby-1", "lobby", "Lobby", ServerState.BOOTING, 0).joinable());
    }

    @Test
    void serverAnzeigeNameSpec08Abschnitt4() {
        assertEquals("Lobby-3", lobby("lobby-3", "lobby", "Lobby", ServerState.ONLINE, 0).serverDisplay());
        assertEquals("Lobby-1", lobby("lobby", "lobby", "Lobby", ServerState.ONLINE, 0).serverDisplay());
        assertEquals("Premium-Lobby-1",
                lobby("premiumlobby-1", "premiumlobby", "Premium-Lobby", ServerState.ONLINE, 0).serverDisplay());
        assertEquals("Silent-Lobby-2",
                lobby("silentlobby-2", "silentlobby", "Silent-Lobby", ServerState.ONLINE, 0).serverDisplay());
        assertEquals("BedWars", game(ServerState.WAITING, 0, 8, 0, false).serverDisplay());
        assertFalse(lobby("lobby-1", "lobby", "Lobby", ServerState.ONLINE, 0).isModeLobby());
    }

    @Test
    void hashFelderUndRundlauf() {
        ServerInfo info = game(ServerState.STARTING, 3, 8, 1, false);
        Map<String, String> hash = info.toHash();
        assertEquals("STARTING", hash.get("state"));
        assertEquals("1", hash.get("joinable"));
        assertEquals("0", hash.get("privateRound"));
        assertEquals(Long.toString(STARTED.toEpochMilli()), hash.get("startedAt"));
        assertEquals("25565", hash.get("port"));
        assertEquals(info, ServerInfo.fromHash(hash));

        Map<String, String> withExtras = new HashMap<>(hash);
        withExtras.put("countdown", "17");
        withExtras.put("build", "0.1.0-SNAPSHOT+abc");
        assertEquals(info, ServerInfo.fromHash(withExtras), "Zusatzfelder des Spiel-Rahmens werden ignoriert");

        ServerInfo lobby = lobby("lobby-2", "lobby", "Lobby", ServerState.ONLINE, 5);
        assertFalse(lobby.toHash().containsKey("map"));
        assertEquals(lobby, ServerInfo.fromHash(lobby.toHash()));
        Map<String, String> broken = new HashMap<>(hash);
        broken.remove("host");
        assertThrows(IllegalArgumentException.class, () -> ServerInfo.fromHash(broken));
    }

    @Test
    void ungueltigeWerte() {
        assertThrows(IllegalArgumentException.class, () -> game(ServerState.WAITING, -1, 8, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ServerInfo("x", "x", "m", "v", ServerCategory.GAME,
                ServerState.WAITING, 0, 1, 0, null, "h", 0, 0, false, 0, false, "X", null, null, STARTED));
    }
}
