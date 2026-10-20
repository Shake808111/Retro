package dev.netzwerk.clanwar;

import dev.netzwerk.game.framework.CombatProfile;
import dev.netzwerk.game.framework.GameDefinition;
import dev.netzwerk.game.framework.MiniGamePlugin;

/** Einstiegspunkt ClanWar. Vorbild, Quellen und offene Punkte: README.md dieses Moduls. */
public final class ClanWarPlugin extends MiniGamePlugin {

    private static final GameDefinition DEFINITION =
            new GameDefinition("clanwar", "ClanWar", CombatProfile.LEGACY_1_8);

    @Override
    protected GameDefinition definition() {
        return DEFINITION;
    }
}
