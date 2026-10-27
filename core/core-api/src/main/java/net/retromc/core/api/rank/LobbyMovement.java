package net.retromc.core.api.rank;

/** Fortbewegung in der Lobby je Rang (Spec 01 §6.2, letzte Zeile). */
public enum LobbyMovement {
    /** Normale Bewegung. */
    NONE,
    /** Doppelsprung (Premium). */
    DOUBLE_JUMP,
    /** Fliegen (ab Premium+). */
    FLY
}
