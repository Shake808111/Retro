package net.retromc.core.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.util.Ulid;
import org.junit.jupiter.api.Test;

class PartyTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
    private static final String ID = Ulid.generate(T0.toEpochMilli(), new Random(7));
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Party party(UUID leader, int maxSize, PartyMember... members) {
        return new Party(ID, leader, T0, false, PublicFilter.ALL, false, maxSize, List.of(members));
    }

    @Test
    void nachfolgerIstDasAeltesteMitglied() {
        Party party = party(LEADER, 10, new PartyMember(B, T0.plusSeconds(20)), new PartyMember(LEADER, T0),
                new PartyMember(A, T0.plusSeconds(10)));
        assertEquals(List.of(LEADER, A, B), party.memberIds());
        assertEquals(Optional.of(A), party.successor());
        Party newLeader = party(B, 10, new PartyMember(LEADER, T0), new PartyMember(A, T0.plusSeconds(10)),
                new PartyMember(B, T0.plusSeconds(20)));
        assertEquals(List.of(B, LEADER, A), newLeader.displayOrder());
        assertEquals(Optional.empty(), party(LEADER, 3, new PartyMember(LEADER, T0)).successor());
    }

    @Test
    void partyLimitSpec01A6() {
        // Leiter mit Rang Spieler: Größe 3 inkl. Leiter → die 4. Person (3. Einladung angenommen) passt nicht
        int maxSize = Rank.DEFAULT.defaultLimits().partySize();
        List<PartyMember> members = new ArrayList<>(List.of(new PartyMember(LEADER, T0)));
        for (int i = 1; i < maxSize; i++) {
            members.add(new PartyMember(new UUID(0, 100 + i), T0.plusSeconds(i)));
        }
        Party full = new Party(ID, LEADER, T0, false, PublicFilter.ALL, false, maxSize, members);
        assertTrue(full.isFull());
        Party premium = new Party(ID, LEADER, T0, false, PublicFilter.ALL, false,
                Rank.PREMIUM.defaultLimits().partySize(), members);
        assertFalse(premium.isFull());
        assertEquals(10, premium.maxSize());
    }

    @Test
    void ungueltigeParty() {
        assertThrows(IllegalArgumentException.class, () -> party(LEADER, 3, new PartyMember(A, T0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Party("p1", LEADER, T0, false, PublicFilter.ALL, false, 3,
                        List.of(new PartyMember(LEADER, T0))));
        assertThrows(IllegalArgumentException.class,
                () -> party(LEADER, 3, new PartyMember(LEADER, T0), new PartyMember(LEADER, T0.plusSeconds(1))));
    }

    @Test
    void oeffentlicheFilterUndEinladung() {
        assertTrue(PublicFilter.ALL.admits(0));
        assertFalse(PublicFilter.PREMIUM.admits(0));
        assertTrue(PublicFilter.PREMIUM.admits(200));
        PartyInvite invite = new PartyInvite(ID, LEADER, A, T0.plus(PartyService.INVITE_TTL));
        assertEquals(LEADER + ";" + T0.plusSeconds(60).toEpochMilli(), invite.hashValue());
        assertEquals(invite, PartyInvite.fromHash(A, ID, invite.hashValue()));
        assertFalse(invite.expired(T0.plusSeconds(59)));
        assertTrue(invite.expired(T0.plusSeconds(60)));
        assertEquals(300, PartyService.OFFLINE_GRACE.toSeconds());
    }
}
