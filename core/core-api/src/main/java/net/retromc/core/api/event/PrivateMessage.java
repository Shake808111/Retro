package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Private Nachricht, Typ {@code PRIVATE_MSG} (net:chat; Spec 01 §8.2). */
public record PrivateMessage(UUID from, List<UUID> to, String text) implements RelayedChat {

    public PrivateMessage {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        to = List.copyOf(to);
    }
}
