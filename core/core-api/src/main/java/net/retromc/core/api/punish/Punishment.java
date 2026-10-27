package net.retromc.core.api.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Strafe aus {@code moderation.punishment} (V1 + V1_1, Spec 01 §2.2, §6.10).
 *
 * @param publicId Anzeige-ID ({@link PunishmentId})
 * @param reasonText freier Text (z. B. {@code /kick}-Grund) oder {@code null}
 * @param expiresAt Ablauf, {@code null} = permanent (bei KICK/WARN ohne Bedeutung)
 * @param acCode Anticheat-Code „(3574)“ oder {@code null}
 * @param stage Stufe des Grundes (1-basiert)
 */
public record Punishment(
        long id,
        String publicId,
        UUID uuid,
        PunishmentType type,
        String reasonCode,
        @Nullable String reasonText,
        @Nullable UUID issuedBy,
        Instant issuedAt,
        @Nullable Instant expiresAt,
        @Nullable Instant revokedAt,
        @Nullable UUID revokedBy,
        @Nullable String revokeNote,
        @Nullable String evidenceRef,
        @Nullable String server,
        PunishmentOrigin origin,
        @Nullable Integer acCode,
        int stage) {

    public Punishment {
        Objects.requireNonNull(publicId, "publicId");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(origin, "origin");
        if (stage < 1) {
            throw new IllegalArgumentException("stage beginnt bei 1");
        }
        if (expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("expiresAt liegt vor issuedAt");
        }
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    /** Status wie in {@code /history}. KICK und WARN gelten sofort als abgelaufen. */
    public PunishmentStatus status(Instant now) {
        if (revokedAt != null) {
            return PunishmentStatus.REVOKED;
        }
        if (type == PunishmentType.KICK || type == PunishmentType.WARN) {
            return PunishmentStatus.EXPIRED;
        }
        return expiresAt == null || now.isBefore(expiresAt) ? PunishmentStatus.ACTIVE : PunishmentStatus.EXPIRED;
    }

    public boolean isActive(Instant now) {
        return status(now) == PunishmentStatus.ACTIVE;
    }

    /** Verhängte Dauer (Ban-Screen „für {duration}“). */
    public PunishmentDuration duration() {
        return expiresAt == null ? PunishmentDuration.PERMANENT
                : PunishmentDuration.of(Duration.between(issuedAt, expiresAt));
    }

    /** Restzeit; leer bei permanenter Strafe, nie negativ. */
    public Optional<Duration> remaining(Instant now) {
        if (expiresAt == null) {
            return Optional.empty();
        }
        Duration left = Duration.between(now, expiresAt);
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }
}
