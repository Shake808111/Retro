package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Clan-Mitglied online/offline ({@code CLAN_ONLINE}/{@code CLAN_OFFLINE}, net:social; Spec 01 §2.4, §6.3 Schritt 6/7); {@code targets} = Empfänger mit {@code notify_clan}. */
public record ClanMemberStatus(UUID uuid, long clanId, List<UUID> targets) {

    public ClanMemberStatus {
        Objects.requireNonNull(uuid, "uuid");
        targets = List.copyOf(targets);
    }
}
