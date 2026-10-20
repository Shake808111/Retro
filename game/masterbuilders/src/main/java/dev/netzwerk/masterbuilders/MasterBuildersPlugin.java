package dev.netzwerk.masterbuilders;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt MasterBuilders. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class MasterBuildersPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("masterbuilders", "MasterBuilders", CombatProfile.MODERN);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
