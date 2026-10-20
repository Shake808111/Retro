package dev.netzwerk.game.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameLifecycleTest {

    @Test
    void normalerRundenablauf() {
        GameLifecycle lifecycle = new GameLifecycle();
        assertEquals(GameState.WAITING, lifecycle.state());
        lifecycle.transitionTo(GameState.STARTING);
        lifecycle.transitionTo(GameState.WAITING); // Countdown abgebrochen
        lifecycle.transitionTo(GameState.STARTING);
        lifecycle.transitionTo(GameState.INGAME);
        lifecycle.transitionTo(GameState.ENDING);
        assertTrue(GameState.ENDING.next().isEmpty());
    }

    @Test
    void unerlaubteUebergaengeWerdenAbgelehnt() {
        GameLifecycle lifecycle = new GameLifecycle();
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(GameState.INGAME));
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(GameState.WAITING));
    }

    @Test
    void modusIdPasstZurDatenbank() {
        new GameDefinition("bedwars", "BedWars", CombatProfile.LEGACY_1_8);
        assertThrows(IllegalArgumentException.class, () -> new GameDefinition("BedWars", "BedWars", CombatProfile.MODERN));
        assertThrows(IllegalArgumentException.class,
                () -> new GameDefinition("a-very-long-mode-id-over-24", "X", CombatProfile.MODERN));
    }
}
