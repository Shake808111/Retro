package dev.netzwerk.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreDataTest {

    private static final UUID PLAYER = UUID.fromString("0f2b6c1e-1111-4222-8333-944455556666");

    @Test
    void schluesselEntsprechenNotiz07() {
        assertEquals("presence:" + PLAYER, ValkeyKeys.presence(PLAYER));
        assertEquals("server:lobby-1", ValkeyKeys.server("lobby-1"));
        assertEquals("queue:bedwars-test", ValkeyKeys.queue("bedwars-test"));
        assertEquals("party:p1:members", ValkeyKeys.partyMembers("p1"));
        assertEquals("player:" + PLAYER + ":party", ValkeyKeys.playerParty(PLAYER));
        assertEquals("lb:bedwars:wins:alltime", ValkeyKeys.leaderboardAllTime("bedwars", "wins"));
        assertEquals("lb:bedwars:wins:2026-10", ValkeyKeys.leaderboardMonthly("bedwars", "wins", YearMonth.of(2026, 10)));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.server("a:b"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.queue(" "));
    }

    @Test
    void datenbankKonfigurationAusUmgebung() {
        DatabaseConfig config = DatabaseConfig.fromMap(Map.of(
                "NETWORK_DB_URL", "jdbc:postgresql://localhost:5432/network",
                "NETWORK_DB_USER", "network",
                "NETWORK_DB_PASSWORD", "geheim"));
        assertEquals(DatabaseConfig.HIKARI_DEFAULT_POOL_SIZE, config.maxPoolSize());
        assertFalse(config.toString().contains("geheim"), "Passwort darf nicht in toString() stehen");
        assertThrows(IllegalStateException.class, () -> DatabaseConfig.fromMap(Map.of()));
    }
}
