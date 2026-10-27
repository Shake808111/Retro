package net.retromc.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreDataTest {

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
