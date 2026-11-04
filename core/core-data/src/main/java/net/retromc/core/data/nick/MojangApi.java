package net.retromc.core.data.nick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.util.PlayerName;
import org.jspecify.annotations.Nullable;

/**
 * Offizielle Mojang-Schnittstellen für den Nick-Pool:
 * <ul>
 *   <li>Namens-API {@code GET …/minecraft/profile/lookup/name/{name}}: 200 = Name vergeben, 404 (früher 204) = frei</li>
 *   <li>Sessionserver {@code GET …/session/minecraft/profile/{uuid}?unsigned=false}: Profil mit signierter
 *       Eigenschaft {@code textures}</li>
 * </ul>
 * Jede Anfrage geht durch den {@link RequestPacer}; HTTP 429 setzt dort die Sperre aus {@code Retry-After}.
 */
public final class MojangApi {

    /** Namens-API (Minecraft-Services). */
    public static final URI NAME_LOOKUP = URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
    /** Sessionserver-Profile. */
    public static final URI SESSION_PROFILE = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");

    /** Ergebnis der Namensprüfung. */
    public enum NameStatus {
        FREE,
        TAKEN,
        RATE_LIMITED,
        /** Netzfehler oder unerwartete Antwort: Name gilt als nicht geprüft. */
        UNKNOWN
    }

    /** Ergebnis eines Textur-Abrufs. */
    public enum FetchStatus {
        OK,
        NOT_FOUND,
        RATE_LIMITED,
        FAILED
    }

    /** Textur-Abruf; {@code texture} nur bei {@link FetchStatus#OK}. */
    public record TextureResult(FetchStatus status, Optional<SkinTexture> texture) {

        public TextureResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(texture, "texture");
        }

        static TextureResult of(FetchStatus status) {
            return new TextureResult(status, Optional.empty());
        }
    }

    private final HttpTransport http;
    private final RequestPacer pacer;
    private final Map<String, String> headers;
    private final URI nameLookup;
    private final URI sessionProfile;

    public MojangApi(HttpTransport http, RequestPacer pacer, String userAgent) {
        this(http, pacer, userAgent, NAME_LOOKUP, SESSION_PROFILE);
    }

    /** Mit anderen Basis-Adressen (Tests, Spiegel); beide enden mit {@code /}. */
    public MojangApi(HttpTransport http, RequestPacer pacer, String userAgent, URI nameLookup, URI sessionProfile) {
        this.http = Objects.requireNonNull(http, "http");
        this.pacer = Objects.requireNonNull(pacer, "pacer");
        this.headers = Map.of("User-Agent", Objects.requireNonNull(userAgent, "userAgent"),
                "Accept", "application/json");
        this.nameLookup = Objects.requireNonNull(nameLookup, "nameLookup");
        this.sessionProfile = Objects.requireNonNull(sessionProfile, "sessionProfile");
    }

    /** Ob ein Name bei Mojang vergeben ist. */
    public NameStatus nameStatus(String name) throws InterruptedException {
        if (!PlayerName.valid(name)) {
            throw new IllegalArgumentException("Ungültiger Name: " + name);
        }
        HttpTransport.Response response = get(nameLookup.resolve(name));
        if (response == null) {
            return NameStatus.UNKNOWN;
        }
        return switch (response.status()) {
            case 200 -> NameStatus.TAKEN;
            case 204, 404 -> NameStatus.FREE;
            case 429 -> NameStatus.RATE_LIMITED;
            default -> NameStatus.UNKNOWN;
        };
    }

    /** Signierte Textur des Profils {@code uuid}. */
    public TextureResult texture(UUID uuid) throws InterruptedException {
        String id = uuid.toString().replace("-", "");
        HttpTransport.Response response = get(sessionProfile.resolve(id + "?unsigned=false"));
        if (response == null) {
            return TextureResult.of(FetchStatus.FAILED);
        }
        return switch (response.status()) {
            case 200 -> parseTextures(id, response.body()).map(texture -> new TextureResult(FetchStatus.OK,
                    Optional.of(texture))).orElseGet(() -> TextureResult.of(FetchStatus.FAILED));
            case 204, 404 -> TextureResult.of(FetchStatus.NOT_FOUND);
            case 429 -> TextureResult.of(FetchStatus.RATE_LIMITED);
            default -> TextureResult.of(FetchStatus.FAILED);
        };
    }

    /** {@code properties[name=textures]} mit Wert und Signatur aus einer Sessionserver-Antwort. */
    static Optional<SkinTexture> parseTextures(String sourceRef, String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject() || !(root.getAsJsonObject().get("properties") instanceof JsonArray list)) {
                return Optional.empty();
            }
            for (JsonElement element : list) {
                if (element instanceof JsonObject property && "textures".equals(text(property, "name"))) {
                    String value = text(property, "value");
                    String signature = text(property, "signature");
                    if (value != null && signature != null && !value.isBlank() && !signature.isBlank()) {
                        return Optional.of(new SkinTexture(sourceRef, value, signature));
                    }
                }
            }
            return Optional.empty();
        } catch (JsonParseException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    static @Nullable String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private HttpTransport.@Nullable Response get(URI uri) throws InterruptedException {
        pacer.await();
        HttpTransport.Response response;
        try {
            response = http.get(uri, headers);
        } catch (IOException e) {
            return null;
        }
        if (response.status() == 429) {
            pacer.backoff(response.retryAfter().orElse(Duration.ZERO));
        }
        return response;
    }
}
