package net.retromc.game.framework;

import java.util.EnumSet;
import java.util.Set;

/**
 * Rundenablauf WAITING -> STARTING -> INGAME -> ENDING (Notiz 07, Abschnitt 5 "Game modes").
 * Nach ENDING wird der Server verworfen ("eine Runde pro Pod", Notiz 07, Abschnitt 3),
 * daher gibt es keinen Übergang zurück nach WAITING.
 */
public enum GameState {
    WAITING,
    STARTING,
    INGAME,
    ENDING;

    /** Erlaubte Folgezustände. STARTING -> WAITING: Countdown abgebrochen (zu wenige Spieler). */
    public Set<GameState> next() {
        return switch (this) {
            case WAITING -> EnumSet.of(STARTING);
            case STARTING -> EnumSet.of(WAITING, INGAME);
            case INGAME -> EnumSet.of(ENDING);
            case ENDING -> EnumSet.noneOf(GameState.class);
        };
    }

    public boolean canTransitionTo(GameState target) {
        return next().contains(target);
    }
}
