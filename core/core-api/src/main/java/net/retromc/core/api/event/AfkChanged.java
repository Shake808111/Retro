package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** AFK-Wechsel ({@code AFK_CHANGED}, net:social; Spec 01 §8.2, §6.12): paper-core → Velocity (Party-Meldung). */
public record AfkChanged(UUID uuid, boolean afk) {

    public AfkChanged {
        Objects.requireNonNull(uuid, "uuid");
    }
}
