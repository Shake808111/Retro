package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Partychat ({@code /p}), Typ {@code PARTY_CHAT} (net:chat; Spec 01 §8.2). */
public record PartyChat(UUID from, List<UUID> to, String text) implements RelayedChat {

    public PartyChat {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        to = List.copyOf(to);
    }
}
