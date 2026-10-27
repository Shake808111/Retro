package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Nick gesetzt oder entfernt ({@code NICK_CHANGED}, net:social; Spec 01 §8.2); {@code nick == null} = entfernt. */
public record NickChanged(UUID uuid, @Nullable String nick, String server) {

    public NickChanged {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(server, "server");
    }
}
