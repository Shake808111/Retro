package dev.netzwerk.survivalgames;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt SurvivalGames. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class SurvivalGamesPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("survivalgames", "SurvivalGames", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
