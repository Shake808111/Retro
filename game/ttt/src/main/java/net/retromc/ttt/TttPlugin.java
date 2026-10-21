package net.retromc.ttt;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt TTT. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class TttPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("ttt", "TTT", CombatProfile.MODERN);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
