package net.retromc.game.framework.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.retromc.game.framework.team.TeamAllocator.Allocation;
import net.retromc.game.framework.team.TeamAllocator.Group;
import net.retromc.game.framework.team.TeamAllocator.Strategy;
import net.retromc.game.framework.team.TeamAllocator.TeamSlot;
import org.junit.jupiter.api.Test;

/**
 * Spec 03 §9 #10: Party bleibt zusammen, wenn Platz; sonst Split + Meldung; Differenz ≤ 1; deterministisch.
 * Spec 04 U5: PREFER_FILLED (3 Solos + Party(3) in 4x4).
 */
class TeamAllocatorTest {

    private static int counter;

    private static UUID id() {
        return new UUID(0, ++counter);
    }

    private static List<UUID> ids(int n) {
        return IntStream.range(0, n).mapToObj(i -> id()).toList();
    }

    private static List<TeamSlot> teams(int count, int size) {
        return IntStream.range(0, count).mapToObj(i -> new TeamSlot(i, size, List.of())).toList();
    }

    private static List<Group> solos(List<UUID> players) {
        return players.stream().map(Group::solo).toList();
    }

    private static List<Integer> sizes(Allocation allocation) {
        return allocation.teams().values().stream().map(List::size).toList();
    }

    private static void assertBalanced(Allocation allocation) {
        List<Integer> nonEmpty = sizes(allocation).stream().filter(size -> size > 0).toList();
        int max = nonEmpty.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = nonEmpty.stream().mapToInt(Integer::intValue).min().orElse(0);
        assertTrue(max - min <= 1, "Differenz > 1: " + sizes(allocation));
    }

    private static void assertTogether(Allocation allocation, List<UUID> party) {
        int team = allocation.teamOf(party.getFirst());
        party.forEach(member -> assertEquals(team, allocation.teamOf(member)));
    }

    @Test
    void solosWerdenAusgeglichen8x4() {
        Allocation allocation = TeamAllocator.allocate(solos(ids(20)), teams(8, 4), Strategy.BALANCE);
        assertBalanced(allocation);
        assertEquals(20, sizes(allocation).stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void dreierPartyBleibtZusammen8x4() {
        List<UUID> party = ids(3);
        List<Group> groups = new ArrayList<>(solos(ids(13)));
        groups.add(new Group(party));
        Allocation allocation = TeamAllocator.allocate(groups, teams(8, 4), Strategy.BALANCE);
        assertTogether(allocation, party);
        assertTrue(allocation.split().isEmpty());
        assertBalanced(allocation);
    }

    @Test
    void viererPartyBleibtZusammen8x4() {
        List<UUID> party = ids(4);
        List<Group> groups = new ArrayList<>(solos(ids(12)));
        groups.add(new Group(party));
        Allocation allocation = TeamAllocator.allocate(groups, teams(8, 4), Strategy.BALANCE);
        assertTogether(allocation, party);
        assertEquals(4, allocation.teams().get(allocation.teamOf(party.getFirst())).size());
        assertTrue(allocation.split().isEmpty());
    }

    @Test
    void fuenferPartyWirdGetrenntUndGemeldet8x4() {
        List<UUID> party = ids(5);
        Allocation allocation = TeamAllocator.allocate(List.of(new Group(party)), teams(8, 4), Strategy.BALANCE);
        assertEquals(Set.copyOf(party), allocation.split());
        assertEquals(4, allocation.teams().get(0).size(), "möglichst großer Teil zusammen");
        assertEquals(1, allocation.teams().get(1).size());
    }

    @Test
    void gewaehlteTeamsBleiben() {
        UUID chosen = id();
        List<TeamSlot> slots = new ArrayList<>(teams(4, 4));
        slots.set(2, new TeamSlot(2, 4, List.of(chosen)));
        Allocation allocation = TeamAllocator.allocate(solos(ids(3)), slots, Strategy.BALANCE);
        assertEquals(2, allocation.teamOf(chosen));
        assertBalanced(allocation);
    }

    @Test
    void partyOhneGanzesFreiesTeamWirdGetrennt() {
        List<TeamSlot> slots = List.of(new TeamSlot(0, 4, ids(2)), new TeamSlot(1, 4, ids(2)));
        List<UUID> party = ids(3);
        Allocation allocation = TeamAllocator.allocate(List.of(new Group(party)), slots, Strategy.BALANCE);
        assertEquals(Set.copyOf(party), allocation.split());
        assertEquals(List.of(4, 3), sizes(allocation));
    }

    @Test
    void groesstePartyZuerst() {
        List<UUID> two = ids(2);
        List<UUID> four = ids(4);
        Allocation allocation = TeamAllocator.allocate(List.of(new Group(two), new Group(four)), teams(2, 4),
                Strategy.BALANCE);
        assertTogether(allocation, four);
        assertTogether(allocation, two);
        assertEquals(0, allocation.teamOf(four.getFirst()));
    }

    @Test
    void gleichstandNachReihenfolge() {
        List<UUID> players = ids(3);
        Allocation allocation = TeamAllocator.allocate(solos(players), teams(4, 1), Strategy.BALANCE);
        assertEquals(0, allocation.teamOf(players.get(0)));
        assertEquals(1, allocation.teamOf(players.get(1)));
        assertEquals(2, allocation.teamOf(players.get(2)));
        assertTrue(allocation.teams().get(3).isEmpty(), "leere Teams sind erlaubt");
    }

    @Test
    void deterministisch() {
        List<UUID> players = ids(11);
        List<UUID> party = ids(3);
        List<Group> groups = new ArrayList<>(solos(players));
        groups.add(new Group(party));
        Allocation first = TeamAllocator.allocate(groups, teams(4, 4), Strategy.BALANCE);
        Allocation second = TeamAllocator.allocate(groups, teams(4, 4), Strategy.BALANCE);
        assertEquals(first, second);
    }

    @Test
    void ueberfuellteRundeMeldetNichtZugeteilte() {
        List<UUID> players = ids(9);
        Allocation allocation = TeamAllocator.allocate(solos(players), teams(4, 2), Strategy.BALANCE);
        assertEquals(1, allocation.unassigned().size());
        assertEquals(players.getLast(), allocation.unassigned().getFirst());
    }

    @Test
    void soloVariante8x1() {
        Allocation allocation = TeamAllocator.allocate(solos(ids(8)), teams(8, 1), Strategy.BALANCE);
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1, 1), sizes(allocation));
    }

    @Test
    void preferFilledDreiSolosUndDreierParty4x4() {
        List<UUID> solos = ids(3);
        List<UUID> party = ids(3);
        List<Group> groups = new ArrayList<>(solos(solos));
        groups.add(new Group(party));
        Allocation allocation = TeamAllocator.allocate(groups, teams(4, 4), Strategy.PREFER_FILLED);
        assertTogether(allocation, party);
        assertEquals(0, allocation.teamOf(party.getFirst()));
        assertEquals(List.of(4, 2, 0, 0), sizes(allocation), "Solos füllen das Party-Team auf");
    }

    @Test
    void preferFilledFuelltBestehendeTeams() {
        List<TeamSlot> slots = List.of(new TeamSlot(0, 4, ids(1)), new TeamSlot(1, 4, ids(3)),
                new TeamSlot(2, 4, List.of()), new TeamSlot(3, 4, List.of()));
        Allocation allocation = TeamAllocator.allocate(solos(ids(2)), slots, Strategy.PREFER_FILLED);
        assertEquals(List.of(2, 4, 0, 0), sizes(allocation));
    }

    @Test
    void preferFilledSorgtFuerGegner() {
        Allocation allocation = TeamAllocator.allocate(solos(ids(2)), teams(4, 4), Strategy.PREFER_FILLED);
        assertEquals(List.of(1, 1, 0, 0), sizes(allocation));
    }

    @Test
    void preferFilledAchtSolosInZweiTeams() {
        Allocation allocation = TeamAllocator.allocate(solos(ids(8)), teams(4, 4), Strategy.PREFER_FILLED);
        assertEquals(List.of(4, 4, 0, 0), sizes(allocation));
    }

    @Test
    void zweiParties8x2() {
        List<UUID> a = ids(2);
        List<UUID> b = ids(2);
        Allocation allocation = TeamAllocator.allocate(List.of(new Group(a), new Group(b), Group.solo(id())),
                teams(8, 2), Strategy.BALANCE);
        assertTogether(allocation, a);
        assertTogether(allocation, b);
        assertTrue(allocation.teamOf(a.getFirst()) != allocation.teamOf(b.getFirst()));
    }
}
