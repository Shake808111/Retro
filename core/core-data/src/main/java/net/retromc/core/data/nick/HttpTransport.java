package net.retromc.core.data.nick;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Einfache HTTP-GET-Schnittstelle für die Nick-Pool-Abrufe (Mojang, MineSkin). Produktiv {@link JdkHttpTransport},
 * in Tests eine Attrappe ohne Netz.
 */
@FunctionalInterface
public interface HttpTransport {

    /** Blockierender GET-Aufruf (läuft auf dem Füll-Thread, nie auf einem Server-Hauptthread). */
    Response get(URI uri, Map<String, String> headers) throws IOException, InterruptedException;

    /**
     * Antwort.
     *
     * @param retryAfter Wartezeit aus {@code Retry-After} (bei 429), sonst leer
     */
    record Response(int status, String body, Optional<Duration> retryAfter) {

        public Response {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(retryAfter, "retryAfter");
        }

        public static Response of(int status, String body) {
            return new Response(status, body, Optional.empty());
        }
    }
}
