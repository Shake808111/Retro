package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Teamchat ({@code /tc}), Typ {@code STAFF_CHAT} (net:chat; Spec 01 §8.2). */
public record StaffChat(UUID from, List<UUID> to, String text) implements RelayedChat {

    public StaffChat {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        to = List.copyOf(to);
    }
}
