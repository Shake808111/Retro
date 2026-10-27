package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Online-Status eines Spielers für seine Freunde ({@code FRIEND_ONLINE}, {@code FRIEND_OFFLINE},
 * {@code FRIEND_SPECTATE}; net:social, Spec 01 §8.2). {@code targets} sind die Empfänger (Proxy-Zustellung).
 */
public record FriendStatus(UUID uuid, Status status, List<UUID> targets) {

    /** ONLINE/OFFLINE (Velocity) oder SPECTATE (Spiel-Rahmen: „schaut dir jetzt zu“). */
    public enum Status {
        ONLINE,
        OFFLINE,
        SPECTATE
    }

    public FriendStatus {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(status, "status");
        targets = List.copyOf(targets);
    }
}
