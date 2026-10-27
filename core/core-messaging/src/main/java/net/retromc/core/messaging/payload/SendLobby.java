package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code SEND_LOBBY {uuids[], reasonKey, mode?}} (net:proxy; Spec 08 §8.3): Server/Controller → Velocity, nutzt den
 * {@code LobbyBalancer}; {@code mode} → bevorzugt Modus-Lobby {@code lobby-{mode}}, sonst Hauptlobby.
 */
public record SendLobby(List<UUID> uuids, String reasonKey, @Nullable String mode) {

    public SendLobby {
        Objects.requireNonNull(reasonKey, "reasonKey");
        uuids = List.copyOf(uuids);
    }
}
