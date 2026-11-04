package net.retromc.game.framework.server;

import java.util.Objects;
import net.retromc.core.api.server.ServerState;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameState;
import net.retromc.paper.core.server.ServerLifecycle;

/**
 * Server-Status für Navigator und Matchmaking (Spec 03 §2.2, §8.1 {@code ServerStatePublisher}): schreibt nur über
 * {@link ServerLifecycle} (Spec 08), nie selbst in {@code server:{name}}. Phasen → Zustand WAITING…ENDING, Zusatzfelder
 * {@code countdown} (jede Sekunde in STARTING, sonst −1), {@code spectatable}, {@code teams}, {@code teamSize},
 * {@code privateId}, {@code owner}; {@code joinable} folgt aus dem Zustand.
 */
public final class ServerStatePublisher {

    private final Game game;

    public ServerStatePublisher(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    /** Beschreibung und feste Zusatzfelder beim Start des Servers. */
    public void attach() {
        game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> {
            lifecycle.describe(game.definition().modeId(), game.variant().key(), game.definition().displayName(),
                    game.maxPlayers());
            lifecycle.setField("spectatable", game.definition().spectatable() ? "1" : "0");
            lifecycle.setField("teams", Integer.toString(game.variant().teams()));
            lifecycle.setField("teamSize", Integer.toString(game.variant().teamSize()));
            lifecycle.setField("countdown", "-1");
            game.privateRound().current().ifPresent(round -> {
                lifecycle.setPrivateRound(true);
                lifecycle.setField("privateId", round.id());
                if (round.owner() != null) {
                    lifecycle.setField("owner", round.owner().toString());
                }
            });
        });
        game.lifecycle().addListener((from, to) -> state(to));
    }

    /** Map geladen: der Server nimmt Spieler an (BOOTING → WAITING, Spec 08 §6.2 Nr. 4). */
    public void ready() {
        game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> {
            lifecycle.setMap(game.maps().current().id());
            lifecycle.setMatchId(game.matchId().toString());
            if (lifecycle.self().state() == ServerState.BOOTING) {
                lifecycle.setState(ServerState.WAITING);
            }
        });
    }

    /** Neue Map ({@code /forcemap}, Abstimmung). */
    public void map(String mapId) {
        game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> lifecycle.setMap(mapId));
    }

    /** Restsekunden des Countdowns oder −1. */
    public void countdown(int rest) {
        game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> lifecycle.setField("countdown",
                Integer.toString(rest)));
    }

    private void state(GameState phase) {
        ServerState target = switch (phase) {
            case WAITING -> ServerState.WAITING;
            case STARTING -> ServerState.STARTING;
            case INGAME -> ServerState.INGAME;
            case ENDING -> ServerState.ENDING;
        };
        game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> {
            lifecycle.setState(target);
            if (phase != GameState.STARTING) {
                lifecycle.setField("countdown", "-1");
            }
        });
    }
}
