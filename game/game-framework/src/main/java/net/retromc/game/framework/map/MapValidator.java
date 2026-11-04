package net.retromc.game.framework.map;

import java.util.ArrayList;
import java.util.List;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.team.TeamColor;

/**
 * Prüfung beim Laden (Spec 03 §6.13, §9 #23): Variante unterstützt, Spawns je Team bzw. {@code spawns.solo} ≥
 * {@code maxPlayers}, Wartelobby vorhanden, Spawns innerhalb {@code bounds}. Eine Map mit Fehlern wird deaktiviert
 * (Log {@code ERROR}); Modi ergänzen eigene Prüfungen.
 */
public final class MapValidator {

    private MapValidator() {
    }

    /**
     * @param teamOrder    Teamreihenfolge des Modus (ohne explizite Liste gelten die ersten {@code teams})
     * @param waitingWorld {@code true}, wenn es eine eigene Wartelobby-Welt gibt
     * @return Fehler, leer = gültig
     */
    public static List<String> validate(GameMap map, TeamMode mode, GameVariant variant, List<TeamColor> teamOrder,
            boolean waitingWorld) {
        List<String> errors = new ArrayList<>();
        MapMeta meta = map.meta();
        if (!meta.supportsVariant(variant.key())) {
            errors.add("Variante " + variant.key() + " nicht unterstützt");
        }
        if (meta.waitingLobby() == null && !waitingWorld) {
            errors.add("waitingLobby fehlt und es gibt keine Wartelobby-Welt");
        }
        if (!meta.bounds().contains(meta.spectatorSpawn().position())) {
            errors.add("spectatorSpawn liegt außerhalb von bounds");
        }
        if (mode == TeamMode.SOLO) {
            List<MapMeta.Spawn> spawns = map.soloSpawns();
            if (spawns.size() < variant.maxPlayers()) {
                errors.add("spawns.solo: " + spawns.size() + " < maxPlayers " + variant.maxPlayers());
            }
            spawns.stream().filter(spawn -> !meta.bounds().contains(spawn.position())).findFirst()
                    .ifPresent(spawn -> errors.add("spawns.solo: Spawn außerhalb von bounds"));
            return errors;
        }
        if (!map.teams().isEmpty() && map.teams().size() < variant.teams()) {
            errors.add("teams: " + map.teams().size() + " < " + variant.teams() + " Teams der Variante");
        }
        for (String team : teamIds(map, variant, mode == TeamMode.NUMBERED ? List.of() : teamOrder)) {
            map.teamSpawn(team).ifPresentOrElse(spawn -> {
                if (!meta.bounds().contains(spawn.position())) {
                    errors.add("spawns." + team + " außerhalb von bounds");
                }
            }, () -> errors.add("spawns." + team + " fehlt"));
        }
        return errors;
    }

    /**
     * IDs der Teams dieser Runde: explizite Liste der Map oder die ersten {@code teams} der Reihenfolge; ohne
     * Reihenfolge (nummerierte Teams {@code Team #n}) {@code 1 … teams}.
     */
    public static List<String> teamIds(GameMap map, GameVariant variant, List<TeamColor> teamOrder) {
        List<String> explicit = map.teams();
        if (!explicit.isEmpty()) {
            return explicit.subList(0, Math.min(explicit.size(), variant.teams()));
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < variant.teams(); i++) {
            ids.add(i < teamOrder.size() ? teamOrder.get(i).id() : Integer.toString(i + 1));
        }
        return ids;
    }
}
