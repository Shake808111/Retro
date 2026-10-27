package net.retromc.core.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheHashTest {

    private static final UUID PLAYER = UUID.fromString("0f2b6c1e-1111-4222-8333-944455556666");
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00.123Z");

    @Test
    void profilHashMitCamelCaseFeldern() {
        Profile profile = new Profile(PLAYER, "Name", "premium", "§6", null, "de_DE", "RTR", 7L, 12, null,
                1_355_470, 4950, 360_000, true);
        Map<String, String> hash = profile.toHash();
        assertEquals("1", hash.get("statsHidden"));
        assertEquals("1355470", hash.get("coins"));
        assertEquals("RTR", hash.get("clanTag"));
        assertEquals("360000", hash.get("playtimeS"));
        assertFalse(hash.containsKey("prefix"));
        assertEquals(profile, Profile.fromHash(PLAYER, hash));
        assertEquals("Name", profile.visibleName());
        Profile minimal = Profile.fromHash(PLAYER, Map.of("name", "A_b", "rank", "default", "color", "§a",
                "locale", "en_US"));
        assertNull(minimal.clanId());
        assertEquals(0, minimal.coins());
        assertThrows(IllegalArgumentException.class, () -> Profile.fromHash(PLAYER, Map.of("name", "x")));
    }

    @Test
    void praesenzHash() {
        Presence presence = new Presence(PLAYER, "proxy-1", "lobby-2", NOW, true, "Nicky", false);
        Map<String, String> hash = presence.toHash();
        assertEquals("1", hash.get("afk"));
        assertEquals("0", hash.get("hidden"));
        assertEquals(presence, Presence.fromHash(PLAYER, hash));
        Presence plain = new Presence(PLAYER, "proxy-1", "lobby-2", NOW, false, null, true);
        assertEquals("", plain.toHash().get("nick"));
        assertEquals(plain, Presence.fromHash(PLAYER, plain.toHash()));
        assertFalse(plain.visible());
    }

    @Test
    void nickHash() {
        Nick nick = new Nick(PLAYER, "Tarnname", "dGV4dHVy", "c2lnbg==", NOW);
        assertEquals(nick, Nick.fromHash(PLAYER, nick.toHash()));
        assertThrows(IllegalArgumentException.class, () -> Nick.fromHash(PLAYER, Map.of("name", "x")));
    }
}
