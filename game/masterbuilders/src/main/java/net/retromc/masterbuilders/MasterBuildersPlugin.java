package net.retromc.masterbuilders;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt MasterBuilders. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class MasterBuildersPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("masterbuilders", "MasterBuilders", CombatProfile.MODERN);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
