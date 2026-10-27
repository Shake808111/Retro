package net.retromc.core.api.punish;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Antrag für {@code /report <Spieler> <Grund> confirm} (Spec 01 §3.6 #75).
 *
 * @param serverMode Modus des Servers (Prüfung {@link ReportReason#allowedOn})
 * @param matchId laufende Runde (Replay-Kontext) oder {@code null}
 */
public record ReportRequest(UUID reporter, UUID target, ReportReason reason, String server, String serverMode,
        @Nullable UUID matchId) {

    public ReportRequest {
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(serverMode, "serverMode");
    }
}
