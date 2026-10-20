package dev.netzwerk.ttt;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt TTT. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class TttPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("ttt", "TTT", CombatProfile.MODERN);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
