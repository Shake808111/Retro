package net.retromc.core.messaging.payload;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** {@code MATCH_ASSIGNED {ticket, server, matchId?, arena?}} (net:queue; Spec 08 §8.3): Controller → Velocity. */
public record MatchAssigned(String ticket, String server, @Nullable String matchId, @Nullable String arena) {

    public MatchAssigned {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(server, "server");
    }
}
