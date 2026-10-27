package net.retromc.core.api.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.util.Ulid;

/**
 * Party aus Valkey ({@code party:{id}} HASH + {@code party:{id}:members} ZSET, Spec 01 §2.3, §6.5). Partys liegen
 * nur in Valkey. Die ID ist eine ULID (§6.5 Nr. 1).
 *
 * @param maxSize Größe inklusive Leiter aus dem Rang des Leiters (bei Leiterwechsel neu, Überzählige bleiben)
 * @param members alle Mitglieder inklusive Leiter
 */
public record Party(
        String id,
        UUID leader,
        Instant created,
        boolean publicParty,
        PublicFilter publicFilter,
        boolean requireRequest,
        int maxSize,
        List<PartyMember> members) {

    public Party {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leader, "leader");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(publicFilter, "publicFilter");
        if (!Ulid.isValid(id)) {
            throw new IllegalArgumentException("Party-ID muss eine ULID sein: " + id);
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize muss ≥ 1 sein");
        }
        List<PartyMember> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(PartyMember::joinedAt).thenComparing(PartyMember::uuid));
        members = List.copyOf(sorted);
        if (members.stream().noneMatch(member -> member.uuid().equals(leader))) {
            throw new IllegalArgumentException("Der Leiter muss Mitglied sein");
        }
        if (members.stream().map(PartyMember::uuid).distinct().count() != members.size()) {
            throw new IllegalArgumentException("Mitglied doppelt");
        }
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean isMember(UUID player) {
        return members.stream().anyMatch(member -> member.uuid().equals(player));
    }

    public int size() {
        return members.size();
    }

    /** Keine neuen Einladungen mehr möglich ({@code party.err.full}). */
    public boolean isFull() {
        return members.size() >= maxSize;
    }

    /** Mitglieder in Beitrittsreihenfolge. */
    public List<UUID> memberIds() {
        return members.stream().map(PartyMember::uuid).toList();
    }

    /** Anzeige-Reihenfolge im Partymenü: Leiter zuerst, dann Beitrittsreihenfolge (Spec 01 §5.1). */
    public List<UUID> displayOrder() {
        List<UUID> order = new ArrayList<>();
        order.add(leader);
        memberIds().stream().filter(uuid -> !uuid.equals(leader)).forEach(order::add);
        return order;
    }

    /** Nachfolger, wenn der Leiter geht: das älteste andere Mitglied (Spec 01 §6.3 Schritt 7). */
    public Optional<UUID> successor() {
        return memberIds().stream().filter(uuid -> !uuid.equals(leader)).findFirst();
    }
}
