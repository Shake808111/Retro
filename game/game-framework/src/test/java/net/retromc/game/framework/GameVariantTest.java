package net.retromc.game.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spec 03 §9 #8: minPlayers = ceil(max/2), Override wirkt. */
class GameVariantTest {

    @Test
    void halbVoll() {
        assertEquals(16, GameVariant.parse("8x4").effectiveMinPlayers());
        assertEquals(4, GameVariant.parse("8x1").effectiveMinPlayers());
        assertEquals(6, GameVariant.parse("12x1").effectiveMinPlayers());
        assertEquals(23, GameVariant.parse("9x5").effectiveMinPlayers());
        assertEquals(32, GameVariant.parse("8x4").maxPlayers());
    }

    @Test
    void overrideUndPrivatrunde() {
        GameVariant variant = new GameVariant("8x4", 8, 4, 10, 20);
        assertEquals(10, variant.effectiveMinPlayers());
        assertEquals(20, variant.effectivePrivateMax());
        assertEquals(32, GameVariant.parse("8x4").effectivePrivateMax());
    }

    @Test
    void teamspielUndAnzeige() {
        assertTrue(GameVariant.parse("4x4").teamPlay());
        assertFalse(GameVariant.parse("8x1").teamPlay());
        assertEquals("8x4", GameVariant.parse("8x4").display());
        assertThrows(IllegalArgumentException.class, () -> GameVariant.parse("default"));
        assertThrows(IllegalArgumentException.class, () -> new GameVariant("2x2", 2, 2, 5, 0));
    }

    @Test
    void einstellungenOhneKonfiguration() {
        GameSettings settings = GameSettings.read(null, "4x4");
        assertEquals(60, settings.countdown());
        assertEquals(30, settings.tipInterval());
        assertEquals("8x1", settings.variant("8x1").key());
        assertEquals("4x4", settings.variant("default").key());
        assertEquals("4x4", settings.variant(null).key());
    }
}
