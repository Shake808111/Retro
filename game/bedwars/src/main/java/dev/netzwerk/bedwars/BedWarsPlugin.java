package dev.netzwerk.bedwars;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt BedWars. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class BedWarsPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("bedwars", "BedWars", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
