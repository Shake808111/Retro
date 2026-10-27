package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Lobby-übergreifender Chat ({@code GLOBAL_CHAT}, net:chat; Spec 01 §8.2, §6.8): paper-core Lobby → alle Lobbys. */
public record GlobalChat(UUID uuid, String server, String text, int level) {

    public GlobalChat {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(text, "text");
    }
}
