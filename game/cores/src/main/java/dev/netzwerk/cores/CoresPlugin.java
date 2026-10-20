package dev.netzwerk.cores;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt Cores. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class CoresPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("cores", "Cores", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
