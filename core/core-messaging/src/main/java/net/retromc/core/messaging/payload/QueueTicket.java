package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code QUEUE_JOIN} / {@code QUEUE_LEAVE {ticket, players[], partyId?, mode, variant, elo?}} (net:queue;
 * Spec 08 §8.3): Lobby → Controller. Eine Party kommt immer als ein Ticket.
 */
public record QueueTicket(String ticket, List<UUID> players, @Nullable String partyId, String mode, String variant,
        @Nullable Integer elo) {

    public QueueTicket {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(variant, "variant");
        players = List.copyOf(players);
    }
}
