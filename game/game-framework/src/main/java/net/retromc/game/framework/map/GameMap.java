package net.retromc.game.framework.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.CombatProfile;
import org.jspecify.annotations.Nullable;

/**
 * Eine Map mit ihren Markern (Spec 03 §6.13, Spec 08 §6.11). Marker-Format des Rahmens unter {@code markers}:
 *
 * <pre>
 * "teams":  ["red", "blue", …]                  optional, explizite Teamliste (sonst die ersten N der Reihenfolge)
 * "spawns": {"red": {x,y,z,yaw,pitch}, …}       Teammodi
 * "spawns": {"solo": [{x,y,z,yaw,pitch}, …]}   Solo-Modi
 * "combat": "MODERN" | "LEGACY_1_8"             optional, sonst Standard des Modus
 * "icon":   "red_bed"                           optional, Material für Map-Menüs
 * </pre>
 *
 * Modusspezifische Marker (Betten, Spawner …) liest das Modul selbst aus {@link #markers()}.
 */
public final class GameMap {

    private final MapMeta meta;
    private final JsonObject markers;

    public GameMap(MapMeta meta) {
        this.meta = Objects.requireNonNull(meta, "meta");
        JsonElement parsed = JsonParser.parseString(meta.markersJson());
        this.markers = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    public MapMeta meta() {
        return meta;
    }

    public String id() {
        return meta.id();
    }

    public String displayName() {
        return meta.displayName();
    }

    /** Marker-Objekt (nicht verändern). */
    public JsonObject markers() {
        return markers;
    }

    /** Builder für die Map-Zeile, durch {@code ", "} getrennt. */
    public String builders() {
        return String.join(", ", meta.builders());
    }

    /** Explizite Teamliste oder leer. */
    public List<String> teams() {
        List<String> teams = new ArrayList<>();
        if (markers.has("teams") && markers.get("teams").isJsonArray()) {
            markers.getAsJsonArray("teams").forEach(element -> teams.add(element.getAsString()
                    .toLowerCase(Locale.ROOT)));
        }
        return teams;
    }

    /** Spawn eines Teams. */
    public Optional<MapMeta.Spawn> teamSpawn(String teamId) {
        JsonObject spawns = object(markers, "spawns");
        return spawns == null ? Optional.empty() : Optional.ofNullable(spawn(spawns.get(teamId)));
    }

    /** Solo-Spawns in Reihenfolge. */
    public List<MapMeta.Spawn> soloSpawns() {
        JsonObject spawns = object(markers, "spawns");
        List<MapMeta.Spawn> result = new ArrayList<>();
        if (spawns != null && spawns.has("solo") && spawns.get("solo").isJsonArray()) {
            for (JsonElement element : spawns.getAsJsonArray("solo")) {
                MapMeta.Spawn spawn = spawn(element);
                if (spawn != null) {
                    result.add(spawn);
                }
            }
        }
        return result;
    }

    /** Kampfprofil der Map oder leer. */
    public Optional<CombatProfile> combat() {
        String value = string(markers, "combat");
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(CombatProfile.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Material-Name des Map-Symbols ({@code paper}, wenn nicht gesetzt). */
    public String icon() {
        String icon = string(markers, "icon");
        return icon == null ? "paper" : icon.toLowerCase(Locale.ROOT);
    }

    /** Ganzzahl-Marker oder {@code fallback}. */
    public int intMarker(String name, int fallback) {
        JsonElement value = markers.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : fallback;
    }

    // ------------------------------------------------------------------------------------------ JSON-Hilfen

    /** Unterobjekt oder {@code null}. */
    public static @Nullable JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    /** Array oder leeres Array. */
    public static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    /** Text oder {@code null}. */
    public static @Nullable String string(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** {@code {x,y,z[,yaw,pitch]}} oder {@code null}. */
    public static MapMeta.@Nullable Spawn spawn(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("x") || !object.has("y") || !object.has("z")) {
            return null;
        }
        return new MapMeta.Spawn(object.get("x").getAsDouble(), object.get("y").getAsDouble(),
                object.get("z").getAsDouble(), object.has("yaw") ? object.get("yaw").getAsFloat() : 0f,
                object.has("pitch") ? object.get("pitch").getAsFloat() : 0f);
    }
}
