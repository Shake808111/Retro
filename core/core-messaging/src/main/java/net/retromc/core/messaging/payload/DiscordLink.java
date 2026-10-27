package net.retromc.core.messaging.payload;

import java.util.Objects;
import java.util.UUID;

/** {@code DISCORD_LINKED} / {@code DISCORD_UNLINKED {uuid, discordId}} (net:integ; Spec 08 §8.3). */
public record DiscordLink(UUID uuid, long discordId) {

    public DiscordLink {
        Objects.requireNonNull(uuid, "uuid");
    }
}
