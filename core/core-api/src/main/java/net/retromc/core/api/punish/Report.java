package net.retromc.core.api.punish;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Meldung aus {@code moderation.report} (V1 + V1_1 {@code public_id}, Spec 01 §6.9). */
public record Report(
        long id,
        String publicId,
        UUID reporter,
        UUID target,
        ReportReason reason,
        String server,
        @Nullable UUID matchId,
        @Nullable UUID chatlogId,
        ReportStatus status,
        @Nullable UUID handledBy,
        Instant createdAt,
        @Nullable Instant handledAt) {

    public Report {
        Objects.requireNonNull(publicId, "publicId");
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
