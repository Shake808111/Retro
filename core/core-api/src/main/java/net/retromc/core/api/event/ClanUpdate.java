package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Clan geändert ({@code CLAN_UPDATE}, net:social; Spec 01 §8.2): Velocity → Paper (Tags in Tab, Nametag, Chat). */
public record ClanUpdate(long clanId, String tag, List<UUID> members) {

    public ClanUpdate {
        Objects.requireNonNull(tag, "tag");
        members = List.copyOf(members);
    }
}
