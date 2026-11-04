package net.retromc.game.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameLifecycleTest {

    @Test
    void normalerRundenablauf() {
        GameLifecycle lifecycle = new GameLifecycle();
        List<String> changes = new ArrayList<>();
        lifecycle.addListener((from, to) -> changes.add(from + ">" + to));
        assertEquals(GameState.WAITING, lifecycle.state());
        assertTrue(lifecycle.waiting());
        lifecycle.transitionTo(GameState.STARTING);
        lifecycle.transitionTo(GameState.WAITING); // Countdown abgebrochen
        lifecycle.transitionTo(GameState.STARTING);
        lifecycle.transitionTo(GameState.INGAME);
        lifecycle.transitionTo(GameState.ENDING);
        assertTrue(GameState.ENDING.next().isEmpty());
        assertEquals(List.of("WAITING>STARTING", "STARTING>WAITING", "WAITING>STARTING", "STARTING>INGAME",
                "INGAME>ENDING"), changes);
    }

    @Test
    void unerlaubteUebergaengeWerdenAbgelehnt() {
        GameLifecycle lifecycle = new GameLifecycle();
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(GameState.INGAME));
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(GameState.WAITING));
    }

    @Test
    void modusIdPasstZurDatenbank() {
        GameDefinition bedwars = new GameDefinition("bedwars", "BedWars", CombatProfile.MODERN);
        assertEquals(TeamMode.TEAMS, bedwars.teamMode());
        new GameDefinition("bedwars_classic", "ClassicBW", CombatProfile.LEGACY_1_8);
        assertThrows(IllegalArgumentException.class, () -> new GameDefinition("BedWars", "BedWars",
                CombatProfile.MODERN));
        assertThrows(IllegalArgumentException.class,
                () -> new GameDefinition("a_very_long_mode_id_over_24", "X", CombatProfile.MODERN));
    }
}
