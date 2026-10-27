package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Offene Freundschaftsanfrage {@code social.friend_request} (GUI §5.3: älteste zuerst, Lore „Gesendet“). */
public record FriendRequest(UUID sender, String senderName, UUID receiver, Instant createdAt) {

    public FriendRequest {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
