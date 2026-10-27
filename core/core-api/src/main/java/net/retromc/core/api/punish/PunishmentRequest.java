package net.retromc.core.api.punish;

import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.util.IpHmac;
import org.jspecify.annotations.Nullable;

/**
 * Antrag für {@code PunishmentService.issue} ({@code /ban}, {@code /mute}, Anticheat, Report). Typ und Stufe ergeben
 * sich aus dem Grund ({@code moderation.reason}); {@code durationOverride} überschreibt die Stufendauer.
 *
 * @param ip HMAC der IP für einen IP-Bann ({@code moderation.ban_ip}) oder {@code null}
 * @param acCode Anticheat-Code bei {@code origin = AC}
 */
public record PunishmentRequest(
        UUID target,
        String reasonCode,
        @Nullable String reasonText,
        @Nullable UUID issuedBy,
        PunishmentOrigin origin,
        @Nullable Integer acCode,
        @Nullable PunishmentDuration durationOverride,
        @Nullable String server,
        @Nullable String evidenceRef,
        @Nullable IpHmac ip) {

    public PunishmentRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(origin, "origin");
        if (acCode != null && origin != PunishmentOrigin.AC) {
            throw new IllegalArgumentException("acCode nur bei origin=AC");
        }
    }

    /** Manuelle Strafe durch ein Teammitglied. */
    public static PunishmentRequest manual(UUID target, String reasonCode, UUID issuedBy,
            @Nullable PunishmentDuration durationOverride, @Nullable String server) {
        return new PunishmentRequest(target, reasonCode, null, issuedBy, PunishmentOrigin.MANUAL, null,
                durationOverride, server, null, null);
    }

    /** Anticheat-Strafe ({@code issue(uuid, "clientmod", origin=AC, acCode)}, Spec 01 §8.3). */
    public static PunishmentRequest anticheat(UUID target, String reasonCode, int acCode, @Nullable String server) {
        return new PunishmentRequest(target, reasonCode, null, null, PunishmentOrigin.AC, acCode, null, server,
                null, null);
    }
}
