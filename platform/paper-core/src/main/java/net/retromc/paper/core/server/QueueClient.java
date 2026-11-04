package net.retromc.paper.core.server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Warteschlange (Spec 08 §8.1 {@code QueueClient}, Spec 03 §6.4): ein Ticket je Spieler bzw. Party. Der
 * Doppelklick-Schutz {@code player:{uuid}:ticket} ({@code SET NX}, TTL 60 s) liegt hier, Matching und Ticket-Hash
 * beim Controller ({@code QUEUE_JOIN}/{@code QUEUE_LEAVE} auf {@code net:queue}).
 */
public interface QueueClient {

    /** Ergebnis von {@link #join}. */
    enum JoinResult {
        /** Ticket angelegt und gesendet. */
        QUEUED,
        /** Es gibt schon ein offenes Ticket ({@code common.spectator.next.already}). */
        ALREADY
    }

    /**
     * Stellt {@code players} (Leiter zuerst) in die Warteschlange.
     *
     * @param source Herkunft {@code NPC}, {@code SPECTATOR_NEXT} oder {@code SIGN}
     */
    CompletableFuture<JoinResult> join(List<UUID> players, String mode, String variant, @Nullable String partyId,
            String source);

    /** Nimmt das offene Ticket eines Spielers zurück. */
    CompletableFuture<Void> leave(UUID player);
}
