package net.retromc.game.framework;

import java.util.Objects;

/** Hält den aktuellen {@link GameState} einer Runde und erzwingt erlaubte Übergänge. */
public final class GameLifecycle {

    private GameState state = GameState.WAITING;

    public GameState state() {
        return state;
    }

    public void transitionTo(GameState target) {
        Objects.requireNonNull(target, "target");
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Übergang " + state + " -> " + target + " ist nicht erlaubt");
        }
        state = target;
    }
}
