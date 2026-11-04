package net.retromc.velocity.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.NickPoolService.Fill;
import net.retromc.core.api.social.NickPoolService.FillStatus;
import net.retromc.core.api.social.NickPoolService.State;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.i18n.Messages;
import org.junit.jupiter.api.Test;

/** Nick und Nick-Pool auf dem Proxy ohne laufenden Velocity (Spec 01 §3.5 #69–72, §6.11). */
class NickProxyTest {

    private static final Instant T = Instant.parse("2026-11-04T12:00:00Z");

    private static Nick nick(UUID uuid, String name) {
        return new Nick(uuid, name, "tex", "sig", T);
    }

    @Test
    void nicksFuerDasTeamEigeneZeileZuerstDannAlphabetisch() {
        PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Moderatorin");
        PlayerRef zora = new PlayerRef(UUID.randomUUID(), "Zora");
        PlayerRef anton = new PlayerRef(UUID.randomUUID(), "anton");
        PlayerRef plain = new PlayerRef(UUID.randomUUID(), "OhneNick");
        Map<UUID, Nick> nicks = Map.of(viewer.uuid(), nick(viewer.uuid(), "SilentFox"),
                zora.uuid(), nick(zora.uuid(), "Keks_2009"), anton.uuid(), nick(anton.uuid(), "xPandax"));
        List<NickFeature.NickLine> lines = NickFeature.teamLines(viewer, List.of(zora, viewer, plain, anton), nicks);
        assertEquals(List.of(new NickFeature.NickLine("nick.list.self", "Moderatorin", "SilentFox"),
                new NickFeature.NickLine("nick.list.line", "anton", "xPandax"),
                new NickFeature.NickLine("nick.list.line", "Zora", "Keks_2009")), lines);
        // Ohne eigenen Nick nur die anderen, ganz ohne Nicks der Hinweis.
        assertEquals(2, NickFeature.teamLines(plain, List.of(zora, anton, plain), nicks).size());
        assertEquals(List.of(new NickFeature.NickLine("nick.err.none", "", "")),
                NickFeature.teamLines(plain, List.of(plain), Map.of()));
    }

    @Test
    void nickNurInDerWartephase() {
        assertFalse(NickFeature.roundRunning(Optional.empty()));
        assertFalse(NickFeature.roundRunning(Optional.of(info(ServerState.WAITING))));
        assertFalse(NickFeature.roundRunning(Optional.of(info(ServerState.STARTING))));
        assertTrue(NickFeature.roundRunning(Optional.of(info(ServerState.INGAME))));
        assertTrue(NickFeature.roundRunning(Optional.of(info(ServerState.ENDING))));
    }

    private static ServerInfo info(ServerState state) {
        return new ServerInfo("bedwars-1", "bedwars-4x4", "bedwars", "4x4", ServerCategory.GAME, state, 0, 16, 0,
                null, "127.0.0.1", 25565, 0, false, 0, false, "BedWars", null, null, T);
    }

    @Test
    void nickPoolAntwortenUndSeiten() {
        assertEquals(List.of("nickpool.fill.done", "nickpool.fill.rejected"),
                NickPoolFeature.fillKeys(new Fill(FillStatus.DONE, 10, 10, 0, 3, 1, 2, 0)));
        assertEquals(List.of("nickpool.fill.done", "nickpool.fill.rejected", "nickpool.fill.ratelimit"),
                NickPoolFeature.fillKeys(new Fill(FillStatus.RATE_LIMITED, 10, 4, 0, 3, 0, 0, 0)));
        assertEquals(List.of("nickpool.fill.ratelimit"), NickPoolFeature.fillKeys(Fill.of(FillStatus.RATE_LIMITED,
                10)));
        assertEquals(List.of("nickpool.err.noskins"), NickPoolFeature.fillKeys(Fill.of(FillStatus.NO_SKINS, 5)));
        assertEquals(List.of("nickpool.err.busy"), NickPoolFeature.fillKeys(Fill.of(FillStatus.BUSY, 5)));
        assertEquals(1, NickPoolFeature.pages(0));
        assertEquals(1, NickPoolFeature.pages(NickPoolFeature.PAGE_SIZE));
        assertEquals(2, NickPoolFeature.pages(NickPoolFeature.PAGE_SIZE + 1));
        for (State state : State.values()) {
            assertTrue(Messages.raw(Messages.DEFAULT_LOCALE, NickPoolFeature.stateKey(state)).length() > 0);
        }
        assertEquals(BuildInfo.BRAND_ID + ".nick.pool", NickPoolFeature.PERMISSION);
    }
}
