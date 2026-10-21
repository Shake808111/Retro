package net.retromc.core.data;

import java.util.Map;
import java.util.Objects;

/**
 * Verbindungsdaten für PostgreSQL.
 *
 * <p>Umgebungsvariablen (siehe docker/docker-compose.dev.yml): {@code NETWORK_DB_URL},
 * {@code NETWORK_DB_USER}, {@code NETWORK_DB_PASSWORD}, optional {@code NETWORK_DB_POOL_SIZE}.
 */
public record DatabaseConfig(String jdbcUrl, String username, String password, int maxPoolSize) {

    /** Standard-Poolgröße von HikariCP, solange nichts anderes konfiguriert ist. */
    public static final int HIKARI_DEFAULT_POOL_SIZE = 10;

    public DatabaseConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize muss mindestens 1 sein");
        }
    }

    public static DatabaseConfig fromEnv() {
        return fromMap(System.getenv());
    }

    static DatabaseConfig fromMap(Map<String, String> env) {
        return new DatabaseConfig(
                require(env, "NETWORK_DB_URL"),
                require(env, "NETWORK_DB_USER"),
                require(env, "NETWORK_DB_PASSWORD"),
                Integer.parseInt(env.getOrDefault("NETWORK_DB_POOL_SIZE", String.valueOf(HIKARI_DEFAULT_POOL_SIZE))));
    }

    private static String require(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable " + key + " fehlt");
        }
        return value;
    }

    @Override
    public String toString() {
        // Passwort nie loggen.
        return "DatabaseConfig[jdbcUrl=" + jdbcUrl + ", username=" + username + ", maxPoolSize=" + maxPoolSize + "]";
    }
}
