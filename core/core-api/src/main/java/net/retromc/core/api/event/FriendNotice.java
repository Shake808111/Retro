package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Freundes-Ereignis {@code FRIEND_REQUEST}, {@code FRIEND_ADDED} oder {@code FRIEND_REMOVED} (net:social, Spec 01 §2.4): {@code actor} hat gegenüber {@code target} gehandelt; zugestellt vom Proxy des Ziels. */
public record FriendNotice(UUID actor, UUID target) {

    public FriendNotice {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
    }
}
