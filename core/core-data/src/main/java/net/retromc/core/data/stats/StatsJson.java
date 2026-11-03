package net.retromc.core.data.stats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.retromc.core.api.stats.TopEntry;
import org.jspecify.annotations.Nullable;

/** JSON der Statistik-Spalten ({@code extra}, {@code points_formula}, {@code fields}) und des Top-Caches. */
final class StatsJson {

    private static final Gson GSON = new Gson();

    private StatsJson() {
    }

    /** Numerische Schlüssel eines JSON-Objekts; andere Werte und ungültiges JSON werden ignoriert. */
    static Map<String, Long> counters(@Nullable String json) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return result;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    result.put(entry.getKey(), value.getAsJsonPrimitive().getAsBigDecimal().longValue());
                }
            }
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            return result;
        }
        return result;
    }

    static List<String> strings(@Nullable String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            root.getAsJsonArray().forEach(element -> result.add(element.getAsString()));
        }
        return result;
    }

    static String write(Map<String, Long> counters) {
        JsonObject object = new JsonObject();
        counters.forEach(object::addProperty);
        return GSON.toJson(object);
    }

    static String writeTop(List<TopEntry> entries) {
        JsonArray array = new JsonArray();
        for (TopEntry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("pos", entry.position());
            object.addProperty("uuid", entry.uuid().toString());
            object.addProperty("name", entry.name());
            object.addProperty("color", entry.color());
            object.addProperty("score", entry.score());
            array.add(object);
        }
        return GSON.toJson(array);
    }

    /** Liest den Top-Cache; bei ungültigem Inhalt leer (dann wird neu aufgebaut). */
    static @Nullable List<TopEntry> readTop(String json) {
        try {
            List<TopEntry> entries = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                entries.add(new TopEntry(object.get("pos").getAsInt(), UUID.fromString(object.get("uuid")
                        .getAsString()), object.get("name").getAsString(), object.get("color").getAsString(),
                        object.get("score").getAsDouble()));
            }
            return entries;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
