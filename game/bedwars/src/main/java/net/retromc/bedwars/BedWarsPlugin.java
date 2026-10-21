package net.retromc.bedwars;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt BedWars. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class BedWarsPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("bedwars", "BedWars", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
