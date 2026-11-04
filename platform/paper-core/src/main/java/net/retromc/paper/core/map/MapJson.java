package net.retromc.paper.core.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.retromc.core.api.map.MapMeta;
import org.jspecify.annotations.Nullable;

/**
 * Liest und schreibt {@code map.json} (Spec 08 §6.11): Pflichtfelder {@code id}, {@code displayName}, {@code mode},
 * {@code version}, {@code builders}, {@code license}, {@code minPlayers}, {@code maxPlayers}, {@code bounds},
 * {@code spectatorSpawn}, {@code markers}; optional {@code variants}, {@code waitingLobby}. Ohne Server testbar.
 */
public final class MapJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private MapJson() {
    }

    /**
     * Liest {@code map.json}.
     *
     * @throws IllegalArgumentException bei ungültigem JSON oder fehlenden/ungültigen Feldern
     */
    public static MapMeta parse(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("map.json ist kein JSON-Objekt");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("map.json ist kein gültiges JSON: " + e.getMessage(), e);
        }
        try {
            JsonObject markers = root.has("markers") && root.get("markers").isJsonObject()
                    ? root.getAsJsonObject("markers") : null;
            if (markers == null) {
                throw new IllegalArgumentException("Pflichtfeld fehlt: markers");
            }
            return new MapMeta(text(root, "id"), text(root, "displayName"), text(root, "mode"),
                    strings(root, "variants"), integer(root, "version"), strings(root, "builders"),
                    text(root, "license"), integer(root, "minPlayers"), integer(root, "maxPlayers"),
                    bounds(required(root, "bounds")), spawn(required(root, "spectatorSpawn")),
                    root.has("waitingLobby") ? spawn(root.get("waitingLobby")) : null, GSON.toJson(markers));
        } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
            throw new IllegalArgumentException("map.json: falscher Feldtyp (" + e.getMessage() + ")", e);
        }
    }

    /** Schreibt {@code map.json} (eingerückt, Felder in Spec-Reihenfolge). */
    public static String write(MapMeta meta) {
        JsonObject root = new JsonObject();
        root.addProperty("id", meta.id());
        root.addProperty("displayName", meta.displayName());
        root.addProperty("mode", meta.mode());
        JsonArray variants = new JsonArray();
        meta.variants().forEach(variants::add);
        root.add("variants", variants);
        root.addProperty("version", meta.version());
        JsonArray builders = new JsonArray();
        meta.builders().forEach(builders::add);
        root.add("builders", builders);
        root.addProperty("license", meta.license());
        root.addProperty("minPlayers", meta.minPlayers());
        root.addProperty("maxPlayers", meta.maxPlayers());
        JsonObject bounds = new JsonObject();
        bounds.add("min", position(meta.bounds().min()));
        bounds.add("max", position(meta.bounds().max()));
        root.add("bounds", bounds);
        root.add("spectatorSpawn", spawn(meta.spectatorSpawn()));
        if (meta.waitingLobby() != null) {
            root.add("waitingLobby", spawn(meta.waitingLobby()));
        }
        root.add("markers", JsonParser.parseString(meta.markersJson()));
        return GSON.toJson(root) + "\n";
    }

    /** Spawn als JSON {@code {x,y,z,yaw,pitch}}. */
    public static JsonObject spawn(MapMeta.Spawn spawn) {
        JsonObject object = new JsonObject();
        object.addProperty("x", spawn.x());
        object.addProperty("y", spawn.y());
        object.addProperty("z", spawn.z());
        object.addProperty("yaw", spawn.yaw());
        object.addProperty("pitch", spawn.pitch());
        return object;
    }

    /** Position als JSON {@code {x,y,z}}. */
    public static JsonObject position(MapMeta.Position position) {
        JsonObject object = new JsonObject();
        object.addProperty("x", position.x());
        object.addProperty("y", position.y());
        object.addProperty("z", position.z());
        return object;
    }

    private static MapMeta.Bounds bounds(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        return new MapMeta.Bounds(position(required(object, "min")), position(required(object, "max")));
    }

    private static MapMeta.Position position(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        return new MapMeta.Position(number(object, "x"), number(object, "y"), number(object, "z"));
    }

    private static MapMeta.Spawn spawn(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        return new MapMeta.Spawn(number(object, "x"), number(object, "y"), number(object, "z"),
                object.has("yaw") ? object.get("yaw").getAsFloat() : 0f,
                object.has("pitch") ? object.get("pitch").getAsFloat() : 0f);
    }

    private static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + field);
        }
        return value;
    }

    private static String text(JsonObject object, String field) {
        return required(object, field).getAsString();
    }

    private static int integer(JsonObject object, String field) {
        return required(object, field).getAsInt();
    }

    private static double number(JsonObject object, String field) {
        return required(object, field).getAsDouble();
    }

    private static List<String> strings(JsonObject object, String field) {
        List<String> values = new ArrayList<>();
        @Nullable JsonElement element = object.get(field);
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> values.add(value.getAsString()));
        }
        return values;
    }
}
