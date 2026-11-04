package net.retromc.game.framework.team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Teamzuteilung beim Start (Spec 03 §6.6 {@code TeamAllocator}, rein deterministisch):
 *
 * <ol>
 *   <li>Gewählte Teams bleiben ({@link TeamSlot#members()}).</li>
 *   <li>Party-Gruppen ohne Team, größte zuerst, in das Team mit den meisten freien Plätzen, das die ganze Gruppe
 *       fasst ({@link Strategy#PREFER_FILLED}: mit den meisten Spielern); passt keine, wird die Gruppe in möglichst
 *       großen Teilen auf die freiesten Teams verteilt und gemeldet ({@code common.team.party_split}).</li>
 *   <li>Einzelspieler: {@link Strategy#BALANCE} ins Team mit den wenigsten Spielern (Gleichstand: kleinere
 *       Reihenfolge-Nummer); ergäbe das eine Differenz &gt; 1 zwischen nicht leeren Teams, bleiben so wenige leere
 *       Teams wie nötig leer. {@link Strategy#PREFER_FILLED} (BedWars, Spec 04 §6.1.2) ins Team mit den meisten
 *       Spielern, das noch Platz hat.</li>
 *   <li>Leere Teams sind erlaubt. Bei {@code PREFER_FILLED} spielen mindestens zwei Teams, sobald es zwei
 *       zuteilbare Spieler gibt (sonst gäbe es keinen Gegner, Annahme).</li>
 * </ol>
 */
public final class TeamAllocator {

    /** Regel für Einzelspieler und Party-Ziel. */
    public enum Strategy {
        /** Ausgleich, Differenz ≤ 1 zwischen nicht leeren Teams (Spec 03). */
        BALANCE,
        /** Bestehende Teams auffüllen (BedWars: „Solos spielen öfter in Teams“). */
        PREFER_FILLED
    }

    /**
     * Ein Team vor der Zuteilung.
     *
     * @param order    Reihenfolge-Nummer (kleiner = zuerst)
     * @param capacity Plätze
     * @param members  bereits gewählte Mitglieder
     */
    public record TeamSlot(int order, int capacity, List<UUID> members) {

        public TeamSlot {
            members = List.copyOf(members);
            if (capacity < 1 || members.size() > capacity) {
                throw new IllegalArgumentException("Team " + order + ": ungültige Belegung");
            }
        }
    }

    /**
     * Spieler ohne Team; eine Gruppe mit mehr als einem Spieler ist eine Party.
     *
     * @param players Spieler in fester Reihenfolge (Leiter zuerst)
     */
    public record Group(List<UUID> players) {

        public Group {
            players = List.copyOf(players);
            if (players.isEmpty()) {
                throw new IllegalArgumentException("leere Gruppe");
            }
        }

        public static Group solo(UUID player) {
            return new Group(List.of(player));
        }

        public boolean party() {
            return players.size() > 1;
        }
    }

    /**
     * Ergebnis.
     *
     * @param teams       Mitglieder je Reihenfolge-Nummer (inklusive der gewählten)
     * @param split       Spieler getrennter Partys (erhalten {@code common.team.party_split})
     * @param unassigned  Spieler ohne freien Platz (Server überfüllt)
     */
    public record Allocation(Map<Integer, List<UUID>> teams, Set<UUID> split, List<UUID> unassigned) {

        public Allocation {
            Map<Integer, List<UUID>> copy = new LinkedHashMap<>();
            teams.forEach((order, members) -> copy.put(order, List.copyOf(members)));
            teams = java.util.Collections.unmodifiableMap(copy);
            split = Set.copyOf(split);
            unassigned = List.copyOf(unassigned);
        }

        /** Reihenfolge-Nummer des Teams eines Spielers oder {@code -1}. */
        public int teamOf(UUID player) {
            for (Map.Entry<Integer, List<UUID>> entry : teams.entrySet()) {
                if (entry.getValue().contains(player)) {
                    return entry.getKey();
                }
            }
            return -1;
        }
    }

    private TeamAllocator() {
    }

    /** Teilt {@code groups} auf {@code teams} auf. */
    public static Allocation allocate(List<Group> groups, List<TeamSlot> teams, Strategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        List<Box> boxes = teams.stream().sorted(Comparator.comparingInt(TeamSlot::order)).map(Box::new).toList();
        Set<UUID> split = new LinkedHashSet<>();
        List<UUID> unassigned = new ArrayList<>();

        List<Group> parties = new ArrayList<>(groups.stream().filter(Group::party).toList());
        parties.sort(Comparator.comparingInt((Group group) -> group.players().size()).reversed());
        for (Group party : parties) {
            Box target = pickForGroup(boxes, party.players().size(), strategy);
            if (target != null) {
                target.members.addAll(party.players());
                continue;
            }
            split.addAll(party.players());
            List<UUID> rest = new ArrayList<>(party.players());
            while (!rest.isEmpty()) {
                Box freest = freest(boxes);
                if (freest == null) {
                    unassigned.addAll(rest);
                    break;
                }
                int take = Math.min(freest.free(), rest.size());
                freest.members.addAll(rest.subList(0, take));
                rest = new ArrayList<>(rest.subList(take, rest.size()));
            }
        }

        List<UUID> singles = groups.stream().filter(group -> !group.party()).map(group -> group.players().getFirst())
                .toList();
        if (strategy == Strategy.BALANCE) {
            placeBalanced(boxes, singles, unassigned);
        } else {
            List<UUID> placedSingles = new ArrayList<>();
            for (UUID single : singles) {
                Box target = fullestWithSpace(boxes);
                if (target == null) {
                    unassigned.add(single);
                } else {
                    target.members.add(single);
                    placedSingles.add(single);
                }
            }
            ensureOpponent(boxes, placedSingles);
        }

        Map<Integer, List<UUID>> result = new LinkedHashMap<>();
        for (Box box : boxes) {
            result.put(box.order, box.members);
        }
        return new Allocation(result, split, unassigned);
    }

    /** Ziel einer ganzen Gruppe: passt sie, das freieste (BALANCE) bzw. vollste (PREFER_FILLED) Team. */
    private static Box pickForGroup(List<Box> boxes, int size, Strategy strategy) {
        Comparator<Box> order = strategy == Strategy.BALANCE
                ? Comparator.comparingInt(Box::free).reversed()
                : Comparator.comparingInt((Box box) -> box.members.size()).reversed();
        return boxes.stream().filter(box -> box.free() >= size).sorted(order.thenComparingInt(box -> box.order))
                .findFirst().orElse(null);
    }

    private static Box freest(List<Box> boxes) {
        return boxes.stream().filter(box -> box.free() > 0)
                .sorted(Comparator.comparingInt(Box::free).reversed().thenComparingInt(box -> box.order))
                .findFirst().orElse(null);
    }

    private static Box emptiest(List<Box> boxes) {
        return boxes.stream().filter(box -> box.free() > 0)
                .sorted(Comparator.comparingInt((Box box) -> box.members.size()).thenComparingInt(box -> box.order))
                .findFirst().orElse(null);
    }

    /**
     * Einzelspieler je ins Team mit den wenigsten Spielern. Wäre die Differenz zwischen nicht leeren Teams dann
     * größer als 1 (z. B. durch eine Party), bleiben so wenige leere Teams wie nötig leer (Spec 03 §6.6 Nr. 4:
     * Ziel Differenz ≤ 1, leere Teams erlaubt).
     */
    private static void placeBalanced(List<Box> boxes, List<UUID> singles, List<UUID> unassigned) {
        if (singles.isEmpty()) {
            return;
        }
        List<Box> occupied = boxes.stream().filter(box -> !box.members.isEmpty()).toList();
        List<Box> empty = boxes.stream().filter(box -> box.members.isEmpty()).toList();
        int bestActive = boxes.size();
        int bestDiff = Integer.MAX_VALUE;
        for (int active = boxes.size(); active >= Math.max(1, occupied.size()); active--) {
            List<Box> chosen = new ArrayList<>(occupied);
            chosen.addAll(empty.subList(0, active - occupied.size()));
            int[] sizes = chosen.stream().mapToInt(box -> box.members.size()).toArray();
            int[] caps = chosen.stream().mapToInt(box -> box.capacity).toArray();
            int left = fill(sizes, caps, singles.size());
            if (left > 0 && active < boxes.size()) {
                continue;
            }
            int diff = spread(sizes);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestActive = active;
            }
            if (diff <= 1) {
                break;
            }
        }
        List<Box> chosen = new ArrayList<>(occupied);
        chosen.addAll(empty.subList(0, bestActive - occupied.size()));
        for (UUID single : singles) {
            Box target = emptiest(chosen);
            if (target == null) {
                unassigned.add(single);
            } else {
                target.members.add(single);
            }
        }
    }

    /** Verteilt {@code count} Spieler auf die kleinsten Teams; liefert den Rest ohne Platz. */
    private static int fill(int[] sizes, int[] caps, int count) {
        int left = count;
        while (left > 0) {
            int target = -1;
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] < caps[i] && (target < 0 || sizes[i] < sizes[target])) {
                    target = i;
                }
            }
            if (target < 0) {
                return left;
            }
            sizes[target]++;
            left--;
        }
        return 0;
    }

    /** Größte minus kleinste Belegung nicht leerer Teams. */
    private static int spread(int[] sizes) {
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (int size : sizes) {
            if (size > 0) {
                max = Math.max(max, size);
                min = Math.min(min, size);
            }
        }
        return min == Integer.MAX_VALUE ? 0 : max - min;
    }

    private static Box fullestWithSpace(List<Box> boxes) {
        return boxes.stream().filter(box -> box.free() > 0)
                .sorted(Comparator.comparingInt((Box box) -> box.members.size()).reversed()
                        .thenComparingInt(box -> box.order))
                .findFirst().orElse(null);
    }

    /** Liegt alles in einem Team, wechselt der zuletzt zugeteilte Einzelspieler ins nächste freie Team. */
    private static void ensureOpponent(List<Box> boxes, List<UUID> placedSingles) {
        long occupied = boxes.stream().filter(box -> !box.members.isEmpty()).count();
        int total = boxes.stream().mapToInt(box -> box.members.size()).sum();
        if (occupied != 1 || total < 2 || placedSingles.isEmpty() || boxes.size() < 2) {
            return;
        }
        UUID mover = placedSingles.getLast();
        Box from = boxes.stream().filter(box -> box.members.contains(mover)).findFirst().orElseThrow();
        Box to = boxes.stream().filter(box -> box.members.isEmpty() && box.free() > 0).findFirst().orElse(null);
        if (to != null) {
            from.members.remove(mover);
            to.members.add(mover);
        }
    }

    /** Veränderbare Belegung während der Zuteilung. */
    private static final class Box {
        private final int order;
        private final int capacity;
        private final List<UUID> members;

        Box(TeamSlot slot) {
            this.order = slot.order();
            this.capacity = slot.capacity();
            this.members = new ArrayList<>(slot.members());
        }

        int free() {
            return capacity - members.size();
        }
    }
}
