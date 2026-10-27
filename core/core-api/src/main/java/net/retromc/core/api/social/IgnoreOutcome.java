package net.retromc.core.api.social;

/** Ergebnis von {@code /ignore <Spieler>} (Spec 01 §3.2 #19, Keys {@code ignore.*}). */
public enum IgnoreOutcome {
    /** {@code ignore.added}. */
    ADDED,
    /** Sich selbst ignorieren. */
    SELF,
    /** {@code error.player.unknown}. */
    UNKNOWN_PLAYER,
    /** Team kann nicht ignoriert werden ({@code ignore.err.staff}). */
    STAFF,
    /** Mehr als 100 Einträge ({@code ignore.err.limit}). */
    LIMIT,
    /** {@code ignore.err.already}. */
    ALREADY
}
