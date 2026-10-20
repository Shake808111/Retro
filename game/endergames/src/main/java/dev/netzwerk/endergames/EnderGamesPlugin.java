package dev.netzwerk.endergames;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt EnderGames. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class EnderGamesPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("endergames", "EnderGames", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
