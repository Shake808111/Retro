package net.retromc.core.data.nick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Zweite, optionale Skin-Quelle: MineSkin (Mojang-signierte Texturen). Der API-Schlüssel kommt nur aus der
 * Umgebung ({@code NETWORK_MINESKIN_KEY}) und wird nie geloggt. Mit festen Skin-IDs werden genau diese geholt
 * ({@code GET /v2/skins/{id}}), sonst die neuesten öffentlichen Skins der Liste ({@code GET /v2/skins?size=n}).
 */
public final class MineSkinApi {

    /** Basis-Adresse der API. */
    public static final URI BASE = URI.create("https://api.mineskin.org/v2/skins/");

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final HttpTransport http;
    private final RequestPacer pacer;
    private final Map<String, String> headers;
    private final URI base;

    public MineSkinApi(HttpTransport http, RequestPacer pacer, String apiKey, String userAgent) {
        this(http, pacer, apiKey, userAgent, BASE);
    }

    /** Mit anderer Basis-Adresse (Tests); endet mit {@code /}. */
    public MineSkinApi(HttpTransport http, RequestPacer pacer, String apiKey, String userAgent, URI base) {
        this.http = Objects.requireNonNull(http, "http");
        this.pacer = Objects.requireNonNull(pacer, "pacer");
        if (Objects.requireNonNull(apiKey, "apiKey").isBlank()) {
            throw new IllegalArgumentException("MineSkin-Schlüssel fehlt");
        }
        this.headers = Map.of("Authorization", "Bearer " + apiKey.strip(),
                "User-Agent", Objects.requireNonNull(userAgent, "userAgent"), "Accept", "application/json");
        this.base = Objects.requireNonNull(base, "base");
    }

    /**
     * Holt Texturen: die angegebenen IDs (ohne {@code known}), sonst bis zu {@code max} aus der öffentlichen Liste.
     * Fehler einzelner Skins werden übersprungen; bei HTTP 429 endet der Abruf.
     */
    public List<SkinTexture> skins(List<String> ids, Set<String> known, int max) throws InterruptedException {
        Set<String> wanted = new LinkedHashSet<>();
        if (ids.isEmpty()) {
            String root = base.toString().endsWith("/") ? base.toString().substring(0, base.toString().length() - 1)
                    : base.toString();
            JsonObject list = getJson(URI.create(root + "?size=" + Math.clamp(max, 1, 64)));
            if (list != null && list.get("skins") instanceof JsonArray entries) {
                List<SkinTexture> inline = new ArrayList<>();
                for (JsonElement entry : entries) {
                    if (entry instanceof JsonObject skin) {
                        String id = MojangApi.text(skin, "uuid");
                        Optional<SkinTexture> texture = id == null ? Optional.empty() : texture(id, skin);
                        if (texture.isPresent() && !known.contains(id)) {
                            inline.add(texture.get());
                        } else if (id != null && !known.contains(id)) {
                            wanted.add(id);
                        }
                    }
                }
                List<SkinTexture> out = new ArrayList<>(inline.subList(0, Math.min(inline.size(), max)));
                fetch(wanted, max - out.size(), out);
                return List.copyOf(out);
            }
            return List.of();
        }
        ids.stream().map(String::strip).filter(id -> ID.matcher(id).matches() && !known.contains(id))
                .forEach(wanted::add);
        List<SkinTexture> out = new ArrayList<>();
        fetch(wanted, max, out);
        return List.copyOf(out);
    }

    private void fetch(Set<String> ids, int limit, List<SkinTexture> out) throws InterruptedException {
        int added = 0;
        for (String id : ids) {
            if (added >= limit || !ID.matcher(id).matches()) {
                continue;
            }
            JsonObject detail = getJson(base.resolve(id));
            if (detail == null) {
                if (pacer.coolingDown()) {
                    return;
                }
                continue;
            }
            JsonObject skin = detail.get("skin") instanceof JsonObject inner ? inner : detail;
            Optional<SkinTexture> texture = texture(id, skin);
            if (texture.isPresent()) {
                out.add(texture.get());
                added++;
            }
        }
    }

    /** Textur aus {@code texture.data} (v2) oder {@code data.texture} (v1). */
    static Optional<SkinTexture> texture(String id, JsonObject skin) {
        JsonObject data = null;
        if (skin.get("texture") instanceof JsonObject texture && texture.get("data") instanceof JsonObject v2) {
            data = v2;
        } else if (skin.get("data") instanceof JsonObject v1 && v1.get("texture") instanceof JsonObject texture) {
            data = texture;
        }
        if (data == null) {
            return Optional.empty();
        }
        String value = MojangApi.text(data, "value");
        String signature = MojangApi.text(data, "signature");
        if (value == null || signature == null || value.isBlank() || signature.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SkinTexture(id, value, signature));
    }

    private @Nullable JsonObject getJson(URI uri) throws InterruptedException {
        pacer.await();
        HttpTransport.Response response;
        try {
            response = http.get(uri, headers);
        } catch (IOException e) {
            return null;
        }
        if (response.status() == 429) {
            pacer.backoff(response.retryAfter().orElse(Duration.ZERO));
            return null;
        }
        if (response.status() != 200) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(response.body());
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }
}
