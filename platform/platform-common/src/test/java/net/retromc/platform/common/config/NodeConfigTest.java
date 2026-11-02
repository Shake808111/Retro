package net.retromc.platform.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Umgebungsvariablen nach Spec 08 §6.2 Nr. 1 und §6.15. */
class NodeConfigTest {

    private static Map<String, String> complete() {
        Map<String, String> env = new HashMap<>();
        env.put(EnvKeys.SERVER_NAME, "lobby-1");
        env.put(EnvKeys.DB_URL, "jdbc:postgresql://postgres:5432/network");
        env.put(EnvKeys.DB_USER, "network");
        env.put(EnvKeys.DB_PASSWORD, "geheim");
        env.put(EnvKeys.VALKEY_URL, "redis://valkey:6379");
        return env;
    }

    @Test
    void liestVollstaendigeKonfigurationMitStandardwerten() {
        NodeConfig config = NodeConfig.read(new EnvReader(complete()), 5);

        assertEquals("lobby-1", config.name());
        assertEquals(NetworkStage.DEV, config.stage());
        assertEquals("jdbc:postgresql://postgres:5432/network", config.database().jdbcUrl());
        assertEquals(5, config.database().maxPoolSize());
        assertEquals("redis://valkey:6379", config.valkeyUrl());
    }

    @Test
    void optionaleWerteUeberschreibenStandards() {
        Map<String, String> env = complete();
        env.put(EnvKeys.DB_POOL_SIZE, "12");
        env.put(EnvKeys.ENV, "Prod");

        NodeConfig config = NodeConfig.read(new EnvReader(env), 5);

        assertEquals(12, config.database().maxPoolSize());
        assertEquals(NetworkStage.PROD, config.stage());
    }

    @Test
    void meldetAlleFehlendenPflichtwerteZusammen() {
        ConfigException error = assertThrows(ConfigException.class,
                () -> NodeConfig.read(new EnvReader(Map.of(EnvKeys.DB_USER, "network")), 5));

        assertEquals(List.of("NETWORK_SERVER_NAME fehlt", "NETWORK_DB_URL fehlt", "NETWORK_DB_PASSWORD fehlt",
                "NETWORK_VALKEY_URL fehlt"), error.problems());
    }

    @Test
    void leereWerteGeltenAlsFehlend() {
        Map<String, String> env = complete();
        env.put(EnvKeys.DB_PASSWORD, "  ");

        ConfigException error = assertThrows(ConfigException.class, () -> NodeConfig.read(new EnvReader(env), 5));

        assertEquals(List.of("NETWORK_DB_PASSWORD fehlt"), error.problems());
    }

    @Test
    void ungueltigeWerteWerdenGemeldet() {
        Map<String, String> env = complete();
        env.put(EnvKeys.SERVER_NAME, "Lobby_1");
        env.put(EnvKeys.DB_POOL_SIZE, "0");
        env.put(EnvKeys.ENV, "test");
        env.put(EnvKeys.VALKEY_URL, "valkey:6379");
        env.put(EnvKeys.DB_URL, "postgres://x");

        ConfigException error = assertThrows(ConfigException.class, () -> NodeConfig.read(new EnvReader(env), 5));

        assertEquals(5, error.problems().size(), error.getMessage());
        assertTrue(error.problems().get(1).contains("dev|staging|prod"), error.getMessage());
    }

    @Test
    void frueherVermerkteProblemeKommenMit() {
        // Plattform-Variablen werden zuerst gelesen; NodeConfig.read meldet dann alle Probleme zusammen.
        EnvReader env = new EnvReader(Map.of(EnvKeys.SERVER_PORT, "x"));
        assertEquals(25565, env.integer(EnvKeys.SERVER_PORT, 25565, 1, 65535));

        ConfigException error = assertThrows(ConfigException.class, () -> NodeConfig.read(env, 5));

        assertEquals("NETWORK_SERVER_PORT 'x' ist keine Zahl von 1 bis 65535", error.problems().getFirst());
        assertEquals(6, error.problems().size(), error.getMessage());
    }

    @Test
    void passwortErscheintNieInTexten() {
        NodeConfig config = NodeConfig.read(new EnvReader(complete()), 5);

        assertFalse(config.toString().contains("geheim"));
    }

    @Test
    void optionalMitMusterLiefertNurGueltigeWerte() {
        EnvReader env = new EnvReader(Map.of("A", "abc", "B", "ABC"));
        Pattern lower = Pattern.compile("[a-z]+");

        assertEquals(Optional.of("abc"), env.optional("A", lower, "a-z"));
        assertEquals(Optional.empty(), env.optional("B", lower, "a-z"));
        assertEquals(Optional.empty(), env.optional("C", lower, "a-z"));
        assertEquals(List.of("B 'ABC' ist ungültig (a-z)"), env.problems());
    }
}
