package net.retromc.bedwars.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.map.GameMap;
import org.jspecify.annotations.Nullable;

/**
 * BedWars-Marker einer Map ({@code map.json → markers}, Spec 04 §6.1.8 Map-Anforderungen):
 *
 * <pre>
 * "spawns":   {"red": {x,y,z,yaw,pitch}, …}      Team-Spawns (Rahmen)
 * "beds":     {"red": {"head": {x,y,z}, "foot": {x,y,z}}, …}
 * "shops":    {"red": {x,y,z,yaw,pitch}, …}      Item-Händler
 * "upgrades": {"red": {x,y,z,yaw,pitch}, …}      Upgrade-Händler (nicht Classic)
 * "chests":   {"red": {x,y,z}, …}                Team-Truhe
 * "spawners": [{"type": "copper", "team": "red", x,y,z}, {"type": "diamond", x,y,z}, …]
 * "buildMaxY": 120,  "voidY": 0,  "waitingWorld": "&lt;Ordner&gt;" (optional)
 * "npc": {"shop": "VILLAGER", "upgrade": "ZOMBIE_VILLAGER"} (optional)
 * </pre>
 */
public final class BedWarsMap {

    /** Ein Spawner-Marker. */
    public record Spawner(Resource type, @Nullable String team, MapMeta.Spawn position) {
    }

    /** Bett eines Teams (Kopf und Fuß). */
    public record Bed(MapMeta.Spawn head, MapMeta.Spawn foot) {
    }

    private final GameMap map;

    public BedWarsMap(GameMap map) {
        this.map = map;
    }

    public GameMap map() {
        return map;
    }

    public Optional<Bed> bed(String team) {
        JsonObject beds = GameMap.object(map.markers(), "beds");
        JsonObject bed = beds == null ? null : GameMap.object(beds, team);
        if (bed == null) {
            return Optional.empty();
        }
        MapMeta.Spawn head = GameMap.spawn(bed.get("head"));
        MapMeta.Spawn foot = GameMap.spawn(bed.get("foot"));
        return head == null || foot == null ? Optional.empty() : Optional.of(new Bed(head, foot));
    }

    public Optional<MapMeta.Spawn> shop(String team) {
        return point("shops", team);
    }

    public Optional<MapMeta.Spawn> upgrade(String team) {
        return point("upgrades", team);
    }

    public Optional<MapMeta.Spawn> chest(String team) {
        return point("chests", team);
    }

    private Optional<MapMeta.Spawn> point(String group, String team) {
        JsonObject section = GameMap.object(map.markers(), group);
        return section == null ? Optional.empty() : Optional.ofNullable(GameMap.spawn(section.get(team)));
    }

    public List<Spawner> spawners() {
        List<Spawner> result = new ArrayList<>();
        for (JsonElement element : GameMap.array(map.markers(), "spawners")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String type = GameMap.string(object, "type");
            MapMeta.Spawn position = GameMap.spawn(object);
            Optional<Resource> resource = type == null ? Optional.empty() : Resource.byId(type);
            if (resource.isPresent() && position != null) {
                result.add(new Spawner(resource.get(), GameMap.string(object, "team"), position));
            }
        }
        return result;
    }

    /** Höchste Bauhöhe ({@code buildMaxY}, Pflicht; ohne Wert Spawn-Höhe + 40 laut Spec, hier Grenze der Map). */
    public int buildMaxY() {
        return map.intMarker("buildMaxY", (int) map.meta().bounds().max().y());
    }

    /** Void-Tod unterhalb dieser Höhe (Standard: Unterkante der {@code bounds}). */
    public int voidY() {
        return map.intMarker("voidY", (int) map.meta().bounds().min().y());
    }

    /** Entity-Typ eines Händlers ({@code shop}/{@code upgrade}), überschreibbar je Map. */
    public String npc(String kind, String fallback) {
        JsonObject npc = GameMap.object(map.markers(), "npc");
        String value = npc == null ? null : GameMap.string(npc, kind);
        return value == null ? fallback : value;
    }

    /**
     * Pflichtmarker (Spec 04 §6.1.8): je Team Bett, Item-Händler, Upgrade-Händler (nicht Classic), Team-Truhe,
     * Basis-Spawner (Kupfer+Eisen bzw. Bronze+Eisen); global ≥ 4 Diamant-Spawner (nicht Classic), ≥ 1 Gold-Spawner,
     * {@code buildMaxY}. Spawns, Zuschauer- und Wartelobby-Spawn prüft der Rahmen.
     */
    public List<String> validate(List<String> teams, boolean classic) {
        List<String> errors = new ArrayList<>();
        if (!map.markers().has("buildMaxY")) {
            errors.add("buildMaxY fehlt");
        }
        Map<String, List<Resource>> base = new LinkedHashMap<>();
        int diamonds = 0;
        int gold = 0;
        for (Spawner spawner : spawners()) {
            if (spawner.team() != null) {
                base.computeIfAbsent(spawner.team(), key -> new ArrayList<>()).add(spawner.type());
            }
            if (spawner.type() == Resource.DIAMOND) {
                diamonds++;
            }
            if (spawner.type() == Resource.GOLD && spawner.team() == null) {
                gold++;
            }
        }
        for (String team : teams) {
            if (bed(team).isEmpty()) {
                errors.add("beds." + team + " fehlt");
            }
            if (shop(team).isEmpty()) {
                errors.add("shops." + team + " fehlt");
            }
            if (!classic && upgrade(team).isEmpty()) {
                errors.add("upgrades." + team + " fehlt");
            }
            if (chest(team).isEmpty()) {
                errors.add("chests." + team + " fehlt");
            }
            List<Resource> own = base.getOrDefault(team, List.of());
            Resource first = classic ? Resource.BRONZE : Resource.COPPER;
            if (!own.contains(first)) {
                errors.add("spawners: " + first.id() + "-Spawner von " + team + " fehlt");
            }
            if (!own.contains(Resource.IRON)) {
                errors.add("spawners: iron-Spawner von " + team + " fehlt");
            }
        }
        if (!classic && diamonds < 4) {
            errors.add("spawners: mindestens 4 diamond-Spawner nötig (" + diamonds + ")");
        }
        if (gold < 1) {
            errors.add("spawners: mindestens 1 gold-Spawner (ohne Team) nötig");
        }
        return errors;
    }
}
