package net.retromc.paper.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.platform.common.config.ConfigException;
import net.retromc.platform.common.config.EnvKeys;
import net.retromc.platform.common.config.EnvReader;
import org.junit.jupiter.api.Test;

/** Konfiguration eines Paper-Servers (Spec 08 §6.2 Nr. 1, §6.5, §6.6, §6.15). */
class ServerConfigTest {

    private static Map<String, String> env(String serverName) {
        Map<String, String> env = new HashMap<>();
        env.put(EnvKeys.SERVER_NAME, serverName);
        env.put(EnvKeys.DB_URL, "jdbc:postgresql://postgres:5432/network");
        env.put(EnvKeys.DB_USER, "network");
        env.put(EnvKeys.DB_PASSWORD, "geheim");
        env.put(EnvKeys.VALKEY_URL, "redis://valkey:6379");
        return env;
    }

    private static ServerConfig read(Map<String, String> env) {
        return ServerConfig.read(new EnvReader(env), () -> "rechner");
    }

    @Test
    void statischeLobbyAusDemComposeGeruest() {
        ServerConfig config = read(env("lobby"));

        assertEquals("lobby", config.name());
        assertEquals(TemplateId.LOBBY, config.template());
        assertEquals(ServerCategory.LOBBY, config.category());
        assertEquals("rechner", config.host());
        assertEquals(25565, config.port());
        assertEquals(ServerConfig.DEFAULT_POOL_SIZE, config.node().database().maxPoolSize());
    }

    @Test
    void templateAusDemServernamen() {
        assertEquals(TemplateId.of("bedwars"), ServerConfig.templateOf("bedwars-1"));
        assertEquals(TemplateId.of("bedwars-4x4"), ServerConfig.templateOf("bedwars-4x4-12"));
        assertEquals(TemplateId.of("lobby-bedwars"), ServerConfig.templateOf("lobby-bedwars-2"));
        assertEquals(TemplateId.of("cb-farm"), ServerConfig.templateOf("cb-farm-2"));
    }

    @Test
    void kategorieDerStandardTemplates() {
        assertEquals(ServerCategory.LOBBY, ServerConfig.categoryOf(TemplateId.PREMIUM_LOBBY));
        assertEquals(ServerCategory.LOBBY, ServerConfig.categoryOf(TemplateId.SILENT_LOBBY));
        assertEquals(ServerCategory.LOBBY, ServerConfig.categoryOf(TemplateId.modeLobby("bedwars")));
        assertEquals(ServerCategory.PERSISTENT, ServerConfig.categoryOf(TemplateId.of("cb-plot")));
        assertEquals(ServerCategory.PERSISTENT, ServerConfig.categoryOf(TemplateId.of("smp-world")));
        assertEquals(ServerCategory.GAME, ServerConfig.categoryOf(TemplateId.of("bedwars-4x4")));
        assertEquals(ServerCategory.GAME, ServerConfig.categoryOf(TemplateId.of("lobbyrun")));
    }

    @Test
    void ausdrueckicheWerteHabenVorrang() {
        Map<String, String> env = env("ffa-1");
        env.put(EnvKeys.SERVER_TEMPLATE, "buildffa");
        env.put(EnvKeys.SERVER_CATEGORY, "persistent");
        env.put(EnvKeys.SERVER_HOST, "net-ffa-1");
        env.put(EnvKeys.SERVER_PORT, "25570");
        env.put(EnvKeys.DB_POOL_SIZE, "8");

        ServerConfig config = read(env);

        assertEquals(TemplateId.of("buildffa"), config.template());
        assertEquals(ServerCategory.PERSISTENT, config.category());
        assertEquals("net-ffa-1", config.host());
        assertEquals(25570, config.port());
        assertEquals(8, config.node().database().maxPoolSize());
    }

    @Test
    void alleProblemeZusammen() {
        Map<String, String> env = env("lobby");
        env.remove(EnvKeys.VALKEY_URL);
        env.put(EnvKeys.SERVER_TEMPLATE, "Lobby!");
        env.put(EnvKeys.SERVER_CATEGORY, "minigame");
        env.put(EnvKeys.SERVER_PORT, "70000");

        ConfigException error = assertThrows(ConfigException.class, () -> read(env));

        assertEquals(4, error.problems().size(), error.getMessage());
        assertTrue(error.problems().contains("NETWORK_VALKEY_URL fehlt"));
    }

    @Test
    void servernameOhneGueltigesTemplate() {
        ConfigException error = assertThrows(ConfigException.class, () -> read(env("x-1")));

        assertEquals(List.of("Aus 'x-1' folgt kein Template; NETWORK_SERVER_TEMPLATE setzen"), error.problems());
    }
}
