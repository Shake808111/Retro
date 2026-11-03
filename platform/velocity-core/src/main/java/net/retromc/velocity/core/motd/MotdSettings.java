package net.retromc.velocity.core.motd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * MOTD-Einstellungen aus {@code core.network_setting['motd']} (Spec 01 §4.1), z. B.
 * {@code {"news":"DUELS NEUE MAPS","maxPlayers":10000,"versionText":"1.21.11+","minProtocol":774}}.
 *
 * @param news Zeile 2 (wird in Großbuchstaben angezeigt), leer = keine Zeile 2
 * @param maxPlayers angezeigte Höchstzahl (Standard 10000)
 * @param versionText Versionstext für zu alte Clients (Standard {@code 1.21.11+})
 * @param minProtocol kleinste unterstützte Protokollnummer (Standard 774 = 1.21.11), 0 = keine Prüfung
 */
public record MotdSettings(String news, int maxPlayers, String versionText, int minProtocol) {

    public static final MotdSettings DEFAULT = new MotdSettings("", 10_000, "1.21.11+", 774);

    public MotdSettings {
        Objects.requireNonNull(news, "news");
        Objects.requireNonNull(versionText, "versionText");
        news = news.strip().toUpperCase(Locale.ROOT);
        if (maxPlayers < 0 || minProtocol < 0) {
            throw new IllegalArgumentException("maxPlayers und minProtocol dürfen nicht negativ sein");
        }
    }

    /** Liest den JSON-Wert; fehlende oder ungültige Felder behalten den Standard. */
    public static MotdSettings parse(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT;
        }
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return new MotdSettings(text(object, "news", DEFAULT.news()), number(object, "maxPlayers",
                    DEFAULT.maxPlayers()), text(object, "versionText", DEFAULT.versionText()),
                    number(object, "minProtocol", DEFAULT.minProtocol()));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException
                | UnsupportedOperationException e) {
            return DEFAULT;
        }
    }

    private static String text(JsonObject object, String field, String fallback) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : fallback;
    }

    private static int number(JsonObject object, String field, int fallback) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : fallback;
    }
}
