package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Clanchat {@code /cc} bzw. Leitungs-Chat {@code /clc} ({@code CLAN_CHAT}, net:chat; Spec 01 §8.2).
 * {@code lead} unterscheidet das Format {@code clan.chat.lead} (Ergänzung zur Spec-Nutzlast).
 */
public record ClanChat(UUID from, List<UUID> to, String text, boolean lead) implements RelayedChat {

    public ClanChat {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        to = List.copyOf(to);
    }
}
