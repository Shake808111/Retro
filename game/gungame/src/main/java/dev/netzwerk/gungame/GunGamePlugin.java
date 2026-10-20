package dev.netzwerk.gungame;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt GunGame. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class GunGamePlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("gungame", "GunGame", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
