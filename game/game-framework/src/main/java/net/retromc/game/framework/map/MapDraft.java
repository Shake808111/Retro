package net.retromc.game.framework.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.retromc.core.api.map.MapMeta;
import org.jspecify.annotations.Nullable;

/**
 * Entwurf einer {@code map.json} für die Setup-Befehle ({@code /bwsetup}, Spec 04 §3): sammelt Name, Autoren,
 * Grenzen, Zuschauer-/Wartelobby-Spawn und die Marker des Modus und baut daraus {@link MapMeta}. Ohne Server
 * testbar; Koordinaten kommen vom Befehl.
 */
public final class MapDraft {

    private final String id;
    private final String mode;
    private String displayName;
    private final Set<String> builders = new LinkedHashSet<>();
    private final Set<String> variants = new LinkedHashSet<>();
    private int version = 1;
    private int minPlayers = 2;
    private int maxPlayers = 16;
    private MapMeta.@Nullable Position corner1;
    private MapMeta.@Nullable Position corner2;
    private MapMeta.@Nullable Spawn spectator;
    private MapMeta.@Nullable Spawn waiting;
    private final JsonObject markers = new JsonObject();

    public MapDraft(String id, String mode) {
        if (!MapMeta.ID.matcher(Objects.requireNonNull(id, "id")).matches()) {
            throw new IllegalArgumentException("Map-ID muss [a-z0-9_]{2,32} sein: " + id);
        }
        this.id = id;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.displayName = id;
    }

    /** Entwurf aus einer bestehenden {@code map.json} (Version steigt beim Speichern). */
    public static MapDraft of(MapMeta meta) {
        MapDraft draft = new MapDraft(meta.id(), meta.mode());
        draft.displayName = meta.displayName();
        draft.builders.addAll(meta.builders());
        draft.variants.addAll(meta.variants());
        draft.version = meta.version() + 1;
        draft.minPlayers = meta.minPlayers();
        draft.maxPlayers = meta.maxPlayers();
        draft.corner1 = meta.bounds().min();
        draft.corner2 = meta.bounds().max();
        draft.spectator = meta.spectatorSpawn();
        draft.waiting = meta.waitingLobby();
        JsonElement parsed = JsonParser.parseString(meta.markersJson());
        if (parsed.isJsonObject()) {
            parsed.getAsJsonObject().entrySet().forEach(entry -> draft.markers.add(entry.getKey(), entry.getValue()));
        }
        return draft;
    }

    public String id() {
        return id;
    }

    public void displayName(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 24 || trimmed.contains("§")) {
            throw new IllegalArgumentException("Name: 1–24 Zeichen ohne §-Codes");
        }
        displayName = trimmed;
    }

    public String displayName() {
        return displayName;
    }

    public void addBuilder(String name) {
        builders.add(name.strip());
    }

    public void clearBuilders() {
        builders.clear();
    }

    public List<String> builders() {
        return List.copyOf(builders);
    }

    public void variants(List<String> values) {
        variants.clear();
        values.stream().map(String::strip).filter(value -> !value.isEmpty()).forEach(variants::add);
    }

    public void players(int min, int max) {
        if (min < 1 || min > max || max > 200) {
            throw new IllegalArgumentException("Es gilt 1 ≤ min ≤ max ≤ 200");
        }
        minPlayers = min;
        maxPlayers = max;
    }

    public void corner(int which, MapMeta.Position position) {
        if (which == 1) {
            corner1 = position;
        } else {
            corner2 = position;
        }
    }

    public void spectator(MapMeta.Spawn spawn) {
        spectator = spawn;
    }

    public void waiting(MapMeta.Spawn spawn, @Nullable String world) {
        waiting = spawn;
        if (world == null) {
            markers.remove("waitingWorld");
        } else {
            markers.addProperty("waitingWorld", world);
        }
    }

    /** Marker-Objekt (direkt bearbeitbar). */
    public JsonObject markers() {
        return markers;
    }

    /** Setzt {@code markers.<group>.<key>}. */
    public void put(String group, String key, JsonElement value) {
        JsonObject section = markers.has(group) && markers.get(group).isJsonObject()
                ? markers.getAsJsonObject(group) : new JsonObject();
        section.add(key, value);
        markers.add(group, section);
    }

    /** Hängt an {@code markers.<group>[]} an. */
    public void append(String group, JsonElement value) {
        JsonArray array = markers.has(group) && markers.get(group).isJsonArray()
                ? markers.getAsJsonArray(group) : new JsonArray();
        array.add(value);
        markers.add(group, array);
    }

    /** Setzt einen einfachen Marker ({@code buildMaxY} …). */
    public void value(String key, Number value) {
        markers.addProperty(key, value);
    }

    /** Was für {@link #build()} noch fehlt. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (builders.isEmpty()) {
            missing.add("builder");
        }
        if (corner1 == null || corner2 == null) {
            missing.add("bounds");
        }
        if (spectator == null) {
            missing.add("spectator");
        }
        return missing;
    }

    /** Baut {@link MapMeta} (Lizenz immer {@code own}). */
    public MapMeta build() {
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Es fehlt: " + String.join(", ", missing));
        }
        MapMeta.Position a = Objects.requireNonNull(corner1);
        MapMeta.Position b = Objects.requireNonNull(corner2);
        MapMeta.Bounds bounds = new MapMeta.Bounds(
                new MapMeta.Position(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new MapMeta.Position(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
        return new MapMeta(id, displayName, mode, List.copyOf(variants), version, List.copyOf(builders),
                MapMeta.LICENSE_OWN, minPlayers, maxPlayers, bounds, Objects.requireNonNull(spectator), waiting,
                markers.toString());
    }

    /** Spawn als JSON {@code {x,y,z,yaw,pitch}} (Blockmitte bei ganzen Koordinaten übernimmt der Befehl). */
    public static JsonObject json(MapMeta.Spawn spawn) {
        JsonObject object = new JsonObject();
        object.addProperty("x", spawn.x());
        object.addProperty("y", spawn.y());
        object.addProperty("z", spawn.z());
        object.addProperty("yaw", spawn.yaw());
        object.addProperty("pitch", spawn.pitch());
        return object;
    }

    /** Blockposition als JSON {@code {x,y,z}}. */
    public static JsonObject json(int x, int y, int z) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        return object;
    }
}
