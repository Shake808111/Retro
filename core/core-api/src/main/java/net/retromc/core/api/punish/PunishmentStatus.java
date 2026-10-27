package net.retromc.core.api.punish;

/** Status in {@code /history} (Spec 01 §7 {@code history.*}: Aktiv, Abgelaufen, Aufgehoben). */
public enum PunishmentStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
