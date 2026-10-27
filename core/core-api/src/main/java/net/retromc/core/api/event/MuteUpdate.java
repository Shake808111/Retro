package net.retromc.core.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.punish.PunishmentType;
import org.jspecify.annotations.Nullable;

/**
 * Mute-Cache aktualisieren ({@code MUTE_UPDATE}, net:moderation; Spec 01 §8.2): Velocity/api/Anticheat → alle.
 *
 * @param id Anzeige-ID der Strafe ({@code public_id})
 * @param expires Ablauf, {@code null} = permanent
 */
public record MuteUpdate(String id, UUID uuid, PunishmentType type, @Nullable Instant expires) {

    public MuteUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(type, "type");
    }
}
