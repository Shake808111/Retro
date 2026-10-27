package net.retromc.core.api.punish;

/** Herkunft, Spalte {@code moderation.punishment.origin} (V1_1, Spec 01 §2.2). */
public enum PunishmentOrigin {
    /** Vom Team per Befehl. */
    MANUAL,
    /** Aus einem Report. */
    REPORT,
    /** Anticheat ({@code ac_code}). */
    AC,
    /** Automatisch (System). */
    AUTO
}
