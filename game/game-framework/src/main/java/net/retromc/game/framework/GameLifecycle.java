package net.retromc.game.framework;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.retromc.core.api.util.Registration;

/** Hält den aktuellen {@link GameState} einer Runde und erzwingt erlaubte Übergänge (Spec 03 §6.5, §8.1). */
public final class GameLifecycle {

    /** Beobachter eines Phasenwechsels. */
    @FunctionalInterface
    public interface PhaseListener {
        void changed(GameState from, GameState to);
    }

    private final List<PhaseListener> listeners = new CopyOnWriteArrayList<>();
    private volatile GameState state = GameState.WAITING;

    public GameState state() {
        return state;
    }

    /** {@code true} in WAITING oder STARTING (Wartelobby). */
    public boolean waiting() {
        GameState current = state;
        return current == GameState.WAITING || current == GameState.STARTING;
    }

    public void transitionTo(GameState target) {
        Objects.requireNonNull(target, "target");
        GameState from = state;
        if (!from.canTransitionTo(target)) {
            throw new IllegalStateException("Übergang " + from + " -> " + target + " ist nicht erlaubt");
        }
        state = target;
        for (PhaseListener listener : listeners) {
            listener.changed(from, target);
        }
    }

    public Registration addListener(PhaseListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }
}
