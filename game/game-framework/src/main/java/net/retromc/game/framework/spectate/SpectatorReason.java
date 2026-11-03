package net.retromc.game.framework.spectate;

/** Auslöser des Zuschauer-Zustands (Spec 03 §6.8 Nr. 1). */
public enum SpectatorReason {
    /** Endgültiger Tod bzw. Ausscheiden. */
    ELIMINATED,
    /** Join in eine laufende Runde ({@code spectatable=1}). */
    LATE_JOIN,
    /** Rundenende: alle Lebenden werden ohne Meldung Zuschauer. */
    ROUND_END
}
