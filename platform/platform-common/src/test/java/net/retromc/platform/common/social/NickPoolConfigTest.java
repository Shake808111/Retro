package net.retromc.platform.common.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.retromc.core.data.nick.NickPoolSettings;
import net.retromc.platform.common.config.EnvReader;
import org.junit.jupiter.api.Test;

/** Nick-Pool-Einstellungen aus der Umgebung (Spec 01 §6.11). */
class NickPoolConfigTest {

    @Test
    void standardOhneVariablen() {
        EnvReader env = new EnvReader(Map.of());
        NickPoolSettings settings = NickPoolConfig.read(env);
        assertEquals(List.of(), settings.skinSources());
        assertTrue(settings.checkMojang());
        assertEquals(Duration.ofMillis(1100), settings.mojangInterval());
        assertNull(settings.mineSkinKey());
        assertFalse(settings.mineSkinEnabled());
        assertTrue(env.problems().isEmpty());
    }

    @Test
    void listenSchalterUndGeheimerSchluessel() {
        EnvReader env = new EnvReader(Map.of(
                "NETWORK_NICK_SKIN_UUIDS", "0a0b0c0d-1e1f-4a2b-8c3d-4e5f6a7b8c9d, 11112222333344445555666677778888",
                "NETWORK_NICK_CHECK_MOJANG", "aus",
                "NETWORK_NICK_MOJANG_INTERVAL_MS", "2000",
                "NETWORK_MINESKIN_KEY", "geheim-123",
                "NETWORK_NICK_MINESKIN_IDS", "abc123,def-456",
                "NETWORK_NICK_BLOCKED_WORDS", "Keks, Brot ,"));
        NickPoolSettings settings = NickPoolConfig.read(env);
        assertEquals(List.of(UUID.fromString("0a0b0c0d-1e1f-4a2b-8c3d-4e5f6a7b8c9d"),
                UUID.fromString("11112222-3333-4444-5555-666677778888")), settings.skinSources());
        assertFalse(settings.checkMojang());
        assertEquals(Duration.ofSeconds(2), settings.mojangInterval());
        assertEquals("geheim-123", settings.mineSkinKey());
        assertEquals(List.of("abc123", "def-456"), settings.mineSkinIds());
        assertEquals(Set.of("keks", "brot"), settings.blockedWords());
        assertFalse(settings.toString().contains("geheim"));
        assertTrue(env.problems().isEmpty());
    }

    @Test
    void ungueltigeWerteWerdenGemeldet() {
        EnvReader env = new EnvReader(Map.of("NETWORK_NICK_SKIN_UUIDS", "keine-uuid",
                "NETWORK_NICK_MOJANG_INTERVAL_MS", "5"));
        NickPoolSettings settings = NickPoolConfig.read(env);
        assertEquals(List.of(), settings.skinSources());
        assertEquals(Duration.ofMillis(1100), settings.mojangInterval());
        assertEquals(2, env.problems().size());
        assertFalse(env.problems().toString().contains("geheim"));
    }
}
