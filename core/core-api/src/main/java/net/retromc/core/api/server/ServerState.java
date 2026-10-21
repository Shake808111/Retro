package net.retromc.core.api.server;

/**
 * Zustand eines Spielservers, wie er im Valkey-Hash {@code server:<name>} (Feld {@code state})
 * veröffentlicht wird. Ablauf nach Notiz 07, Abschnitt 3 (Controller-Fluss, Schritt 4):
 * WAITING -> STARTING -> INGAME -> ENDING.
 */
public enum ServerState {
    /** Wartelobby, Spieler können beitreten. */
    WAITING,
    /** Countdown läuft. */
    STARTING,
    /** Runde läuft. */
    INGAME,
    /** Runde beendet; der Server wird danach verworfen (eine Runde pro Pod, Notiz 07 Abschnitt 3). */
    ENDING;

    /** Ob neue Spieler in diesem Zustand per Queue zugewiesen werden dürfen. */
    public boolean acceptsPlayers() {
        return this == WAITING || this == STARTING;
    }
}
