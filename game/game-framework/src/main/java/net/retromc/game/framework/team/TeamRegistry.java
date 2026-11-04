package net.retromc.game.framework.team;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapValidator;
import org.jspecify.annotations.Nullable;

/**
 * Teams der Runde (Spec 03 §6.6, §8.1 {@code TeamRegistry}): Aufbau aus Map und Variante, freie Wahl bis Rest
 * &gt; 10 s, Zuteilung über {@link TeamAllocator}.
 */
public final class TeamRegistry {

    /** Ergebnis einer Teamwahl. */
    public enum JoinResult {
        OK, SAME, FULL, LOCKED
    }

    private final TeamMode mode;
    private final GameVariant variant;
    private final List<TeamColor> order;
    private final List<GameTeam> teams = new CopyOnWriteArrayList<>();

    public TeamRegistry(TeamMode mode, GameVariant variant, List<TeamColor> order) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.variant = Objects.requireNonNull(variant, "variant");
        this.order = List.copyOf(order);
    }

    /** Baut die Teams für {@code map}; Mitglieder gleicher Team-IDs bleiben (z. B. nach {@code /forcemap}). */
    public void build(GameMap map) {
        Map<String, List<UUID>> previous = new LinkedHashMap<>();
        teams.forEach(team -> previous.put(team.id(), team.members()));
        List<GameTeam> built = new ArrayList<>();
        if (mode == TeamMode.SOLO) {
            for (int i = 0; i < variant.maxPlayers(); i++) {
                built.add(new GameTeam(i, Integer.toString(i + 1), null, 1));
            }
        } else {
            List<String> ids = MapValidator.teamIds(map, variant, mode == TeamMode.NUMBERED ? List.of() : order);
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                TeamColor color = mode == TeamMode.NUMBERED ? null : TeamColor.byId(id).orElse(null);
                built.add(new GameTeam(i, id, color, variant.teamSize()));
            }
        }
        for (GameTeam team : built) {
            previous.getOrDefault(team.id(), List.of()).forEach(team::add);
        }
        teams.clear();
        teams.addAll(built);
    }

    public List<GameTeam> teams() {
        return List.copyOf(teams);
    }

    public Optional<GameTeam> byId(String id) {
        return teams.stream().filter(team -> team.id().equalsIgnoreCase(id)).findFirst();
    }

    public Optional<GameTeam> byIndex(int index) {
        return teams.stream().filter(team -> team.index() == index).findFirst();
    }

    public @Nullable GameTeam teamOf(UUID player) {
        for (GameTeam team : teams) {
            if (team.contains(player)) {
                return team;
            }
        }
        return null;
    }

    /** Wählt ein Team (die Prüfung von Phase/Rest übernimmt der Aufrufer über {@code choicesOpen}). */
    public JoinResult join(UUID player, GameTeam team, boolean choicesOpen) {
        if (!choicesOpen) {
            return JoinResult.LOCKED;
        }
        if (team.contains(player)) {
            return JoinResult.SAME;
        }
        if (team.full()) {
            return JoinResult.FULL;
        }
        assign(player, team);
        return JoinResult.OK;
    }

    /** Setzt das Team ohne Prüfung. */
    public void assign(UUID player, GameTeam team) {
        leave(player);
        team.add(player);
    }

    public void leave(UUID player) {
        teams.forEach(team -> team.remove(player));
    }

    /**
     * Verteilt {@code unteamed} nach {@code strategy}; Spieler derselben Party (gleicher Schlüssel in
     * {@code partyOf}) bilden eine Gruppe in Reihenfolge von {@code unteamed}.
     *
     * @return Ergebnis (getrennte Partys, nicht zuteilbare Spieler)
     */
    public TeamAllocator.Allocation allocate(List<UUID> unteamed, Map<UUID, String> partyOf,
            TeamAllocator.Strategy strategy) {
        Map<String, List<UUID>> parties = new LinkedHashMap<>();
        List<TeamAllocator.Group> groups = new ArrayList<>();
        for (UUID player : unteamed) {
            String party = partyOf.get(player);
            if (party == null) {
                groups.add(TeamAllocator.Group.solo(player));
            } else {
                parties.computeIfAbsent(party, key -> new ArrayList<>()).add(player);
            }
        }
        parties.values().forEach(members -> groups.add(new TeamAllocator.Group(members)));
        List<TeamAllocator.TeamSlot> slots = teams.stream()
                .map(team -> new TeamAllocator.TeamSlot(team.index(), team.capacity(), team.members())).toList();
        TeamAllocator.Allocation allocation = TeamAllocator.allocate(groups, slots, strategy);
        allocation.teams().forEach((index, members) -> byIndex(index).ifPresent(team -> members.forEach(member -> {
            if (!team.contains(member)) {
                assign(member, team);
            }
        })));
        return allocation;
    }

    /** Teams mit mindestens einem Mitglied, das {@code alive} erfüllt. */
    public List<GameTeam> alive(java.util.function.Predicate<UUID> alive) {
        return teams.stream().filter(team -> team.members().stream().anyMatch(alive)).toList();
    }
}
