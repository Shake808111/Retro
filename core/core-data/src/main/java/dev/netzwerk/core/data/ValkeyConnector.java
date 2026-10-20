package dev.netzwerk.core.data;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Verbindung zu Valkey über den Redis-kompatiblen Client Lettuce (Notiz 07, Abschnitt 4).
 * URL-Beispiel: {@code redis://valkey:6379} (Umgebungsvariable {@code NETWORK_VALKEY_URL}).
 */
public final class ValkeyConnector implements AutoCloseable {

    private final RedisClient client;

    public ValkeyConnector(String valkeyUrl) {
        this.client = RedisClient.create(valkeyUrl);
    }

    public static ValkeyConnector fromEnv() {
        String url = System.getenv("NETWORK_VALKEY_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable NETWORK_VALKEY_URL fehlt");
        }
        return new ValkeyConnector(url);
    }

    /** Öffnet eine neue Verbindung (thread-sicher, für viele Befehle wiederverwendbar). */
    public StatefulRedisConnection<String, String> connect() {
        return client.connect();
    }

    public RedisClient client() {
        return client;
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
