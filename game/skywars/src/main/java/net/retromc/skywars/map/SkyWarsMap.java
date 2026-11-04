package net.retromc.skywars.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.map.GameMap;
import org.jspecify.annotations.Nullable;

/**
 * SkyWars-Marker einer Map (Spec 04 §6.2.2, §8.3: Inseln, Truhen mit Tier, Mitte-Region, {@code voidY}, Border):
 *
 * <pre>
 * "islands": [{"spawns": [{x,y,z,yaw,pitch} …], "chests": [{x,y,z} …]} …]   Insel n = Team n
 * "mid":     {"min": {x,y,z}, "max": {x,y,z}, "chests": [{x,y,z} …]}         Tier 2, Schneebälle/Eier nur hier
 * "voidY": 0,  "border": {"x": 0.5, "z": 0.5, "radius": 90}
 * "kitSettings": {x,y,z,yaw,pitch}   optional: Hologramm „Kit-Einstellungen“ in der Wartelobby
 * "teams": ["1" …], "spawns": {"1": {…}, "solo": [{…} …]}   schreibt /swsetup save aus den Inseln (Rahmen)
 * </pre>
 *
 * Inseltruhen und -fässer: 2–3 je Insel (Tier 1), Mitte: 4–8 (Tier 2), Spawns je Insel ≥ Teamgröße (§6.2.2).
 */
public final class SkyWarsMap {

    /** Container je Insel (B). */
    public static final int ISLAND_CHESTS_MIN = 2;
    public static final int ISLAND_CHESTS_MAX = 3;
    /** Container der Mitte (B). */
    public static final int MID_CHESTS_MIN = 4;
    public static final int MID_CHESTS_MAX = 8;

    /** Blockposition. */
    public record Block(int x, int y, int z) {
    }

    /** Eine Insel: Spawns je Spieler und Tier-1-Container. */
    public record Island(List<MapMeta.Spawn> spawns, List<Block> chests) {

        public Island {
            spawns = List.copyOf(spawns);
            chests = List.copyOf(chests);
        }
    }

    /** Mitte: Region und Tier-2-Container. */
    public record Mid(MapMeta.@Nullable Position min, MapMeta.@Nullable Position max, List<Block> chests) {

        public Mid {
            chests = List.copyOf(chests);
        }

        /** Ob ein Punkt in der Mitte-Region liegt (ohne Region: nie). */
        public boolean contains(double x, double y, double z) {
            if (min == null || max == null) {
                return false;
            }
            return x >= Math.min(min.x(), max.x()) && x <= Math.max(min.x(), max.x()) + 1
                    && y >= Math.min(min.y(), max.y()) && y <= Math.max(min.y(), max.y()) + 1
                    && z >= Math.min(min.z(), max.z()) && z <= Math.max(min.z(), max.z()) + 1;
        }
    }

    private final GameMap map;

    public SkyWarsMap(GameMap map) {
        this.map = Objects.requireNonNull(map, "map");
    }

    public GameMap map() {
        return map;
    }

    public List<Island> islands() {
        List<Island> islands = new ArrayList<>();
        for (JsonElement element : GameMap.array(map.markers(), "islands")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject island = element.getAsJsonObject();
            List<MapMeta.Spawn> spawns = new ArrayList<>();
            for (JsonElement spawn : GameMap.array(island, "spawns")) {
                MapMeta.Spawn parsed = GameMap.spawn(spawn);
                if (parsed != null) {
                    spawns.add(parsed);
                }
            }
            islands.add(new Island(spawns, blocks(GameMap.array(island, "chests"))));
        }
        return islands;
    }

    /** Insel eines Teams (Index ab 0). */
    public Optional<Island> island(int index) {
        List<Island> islands = islands();
        return index >= 0 && index < islands.size() ? Optional.of(islands.get(index)) : Optional.empty();
    }

    public Mid mid() {
        JsonObject mid = GameMap.object(map.markers(), "mid");
        if (mid == null) {
            return new Mid(null, null, List.of());
        }
        return new Mid(position(GameMap.object(mid, "min")), position(GameMap.object(mid, "max")),
                blocks(GameMap.array(mid, "chests")));
    }

    /** Tod unterhalb dieser Höhe (Standard: Unterkante der {@code bounds}). */
    public int voidY() {
        return map.intMarker("voidY", (int) map.meta().bounds().min().y());
    }

    /** Border-Zentrum x. */
    public double borderX() {
        JsonObject border = GameMap.object(map.markers(), "border");
        return border != null && border.has("x") ? border.get("x").getAsDouble() : center(true);
    }

    /** Border-Zentrum z. */
    public double borderZ() {
        JsonObject border = GameMap.object(map.markers(), "border");
        return border != null && border.has("z") ? border.get("z").getAsDouble() : center(false);
    }

    /** Border-Radius zu Beginn (Standard: halbe größere Kante der {@code bounds}). */
    public double borderRadius() {
        JsonObject border = GameMap.object(map.markers(), "border");
        if (border != null && border.has("radius")) {
            return border.get("radius").getAsDouble();
        }
        MapMeta.Bounds bounds = map.meta().bounds();
        return Math.max(bounds.max().x() - bounds.min().x(), bounds.max().z() - bounds.min().z()) / 2.0;
    }

    private double center(boolean x) {
        MapMeta.Bounds bounds = map.meta().bounds();
        return x ? (bounds.min().x() + bounds.max().x()) / 2.0 : (bounds.min().z() + bounds.max().z()) / 2.0;
    }

    /** Ort des Hologramms „Kit-Einstellungen“ oder leer. */
    public Optional<MapMeta.Spawn> kitSettings() {
        return Optional.ofNullable(GameMap.spawn(map.markers().get("kitSettings")));
    }

    /**
     * Prüfung für eine Variante (Spec 04 §6.2.2); Spawns je Team und Zuschauer-/Wartelobby-Spawn prüft der Rahmen.
     *
     * @return Fehler, leer = gültig
     */
    public List<String> validate(GameVariant variant) {
        List<String> errors = new ArrayList<>();
        List<Island> islands = islands();
        MapMeta.Bounds bounds = map.meta().bounds();
        if (islands.size() < variant.teams()) {
            errors.add("islands: " + islands.size() + " < " + variant.teams() + " Teams der Variante");
        }
        for (int i = 0; i < Math.min(islands.size(), variant.teams()); i++) {
            Island island = islands.get(i);
            String name = "islands[" + (i + 1) + "]";
            if (island.spawns().size() < variant.teamSize()) {
                errors.add(name + ": " + island.spawns().size() + " Spawns < Teamgröße " + variant.teamSize());
            }
            if (island.chests().size() < ISLAND_CHESTS_MIN || island.chests().size() > ISLAND_CHESTS_MAX) {
                errors.add(name + ": " + island.chests().size() + " Truhen (erlaubt " + ISLAND_CHESTS_MIN + "–"
                        + ISLAND_CHESTS_MAX + ")");
            }
            if (island.spawns().stream().anyMatch(spawn -> !bounds.contains(spawn.position()))
                    || island.chests().stream().anyMatch(block -> !inside(bounds, block))) {
                errors.add(name + ": Marker außerhalb von bounds");
            }
        }
        Mid mid = mid();
        if (mid.min() == null || mid.max() == null) {
            errors.add("mid: Region (min/max) fehlt");
        }
        if (mid.chests().size() < MID_CHESTS_MIN || mid.chests().size() > MID_CHESTS_MAX) {
            errors.add("mid: " + mid.chests().size() + " Truhen (erlaubt " + MID_CHESTS_MIN + "–" + MID_CHESTS_MAX
                    + ")");
        }
        if (mid.chests().stream().anyMatch(block -> !inside(bounds, block))) {
            errors.add("mid: Truhe außerhalb von bounds");
        }
        if (!map.markers().has("voidY")) {
            errors.add("voidY fehlt");
        }
        if (borderRadius() <= 0) {
            errors.add("border.radius muss > 0 sein");
        }
        return errors;
    }

    private static boolean inside(MapMeta.Bounds bounds, Block block) {
        return bounds.contains(new MapMeta.Position(block.x(), block.y(), block.z()));
    }

    private static List<Block> blocks(JsonArray array) {
        List<Block> blocks = new ArrayList<>();
        for (JsonElement element : array) {
            MapMeta.Position position = element.isJsonObject() ? position(element.getAsJsonObject()) : null;
            if (position != null) {
                blocks.add(new Block((int) Math.floor(position.x()), (int) Math.floor(position.y()),
                        (int) Math.floor(position.z())));
            }
        }
        return blocks;
    }

    private static MapMeta.@Nullable Position position(@Nullable JsonObject object) {
        if (object == null || !object.has("x") || !object.has("y") || !object.has("z")) {
            return null;
        }
        return new MapMeta.Position(object.get("x").getAsDouble(), object.get("y").getAsDouble(),
                object.get("z").getAsDouble());
    }
}
