package dev.netzwerk.skywars;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt SkyWars. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class SkyWarsPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("skywars", "SkyWars", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
