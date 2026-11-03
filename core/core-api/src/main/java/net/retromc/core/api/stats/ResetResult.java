package net.retromc.core.api.stats;

/** Ausgang eines Statistik-Resets (Spec 02 §6.9). */
public enum ResetResult {
    /** Gelöscht, Snapshot im {@code reset_log}, bei {@link ResetScope#PLAYER} 1 Token verbraucht. */
    DONE,
    /** Kein ResetToken ({@code reset.no-token}). */
    NO_TOKEN,
    /** Modus nicht zurücksetzbar ({@code reset.not-resettable}). */
    NOT_RESETTABLE,
    /** Modus unbekannt ({@code stats.unknown-mode}). */
    UNKNOWN_MODE
}
