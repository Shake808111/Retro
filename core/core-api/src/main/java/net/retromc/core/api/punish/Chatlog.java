package net.retromc.core.api.punish;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Chatlog {@code moderation.chatlog} (Spec 01 §3.6 #76, §6.9 Nr. 3; Web-Seite {@code GET /v1/chatlog/{id}}). */
public record Chatlog(UUID id, @Nullable UUID createdBy, UUID target, String server, List<ChatLine> messages,
        Instant createdAt) {

    public Chatlog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(createdAt, "createdAt");
        messages = List.copyOf(messages);
    }
}
