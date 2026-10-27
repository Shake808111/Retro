package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** {@code QUEUE_FAILED {ticket, players[], reason}} (net:queue; Spec 08 §8.3, §6.8): Controller → Lobby. */
public record QueueFailed(String ticket, List<UUID> players, Reason reason) {

    /** Gründe: 60 s ohne Server, Party zu groß für die Teamgröße, kein Template. */
    public enum Reason {
        TIMEOUT,
        PARTY_TOO_BIG,
        NO_TEMPLATE
    }

    public QueueFailed {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(reason, "reason");
        players = List.copyOf(players);
    }
}
