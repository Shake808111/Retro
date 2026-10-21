package net.retromc.clanwar;

import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.MiniGamePlugin;

/** Einstiegspunkt ClanWar. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class ClanWarPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("clanwar", "ClanWar", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
