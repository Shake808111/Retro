package net.retromc.gungame;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt GunGame. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class GunGamePlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("gungame", "GunGame", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
