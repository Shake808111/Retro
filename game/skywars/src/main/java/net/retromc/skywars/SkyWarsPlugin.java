package net.retromc.skywars;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt SkyWars. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class SkyWarsPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("skywars", "SkyWars", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
