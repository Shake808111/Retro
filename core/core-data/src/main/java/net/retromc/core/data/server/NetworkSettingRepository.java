package net.retromc.core.data.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.server.NetworkSettingService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/** {@link NetworkSettingService} auf {@code core.network_setting (key, value JSONB)} (Spec 01 §2.2, /netreload). */
public final class NetworkSettingRepository implements NetworkSettingService {

    private final AsyncDatabase database;

    public NetworkSettingRepository(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<Optional<String>> get(String key) {
        return database.query(connection -> Jdbc.one(connection, "SELECT value::text AS value FROM"
                + " core.network_setting WHERE key = ?", row -> row.getString("value"), key));
    }

    @Override
    public CompletableFuture<Void> put(String key, String json) {
        return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO core.network_setting"
                + " (key, value, updated_at) VALUES (?, ?::jsonb, now()) ON CONFLICT (key) DO UPDATE"
                + " SET value = EXCLUDED.value, updated_at = now()", key, json)).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Map<String, String>> all() {
        return database.query(connection -> {
            Map<String, String> values = new LinkedHashMap<>();
            Jdbc.list(connection, "SELECT key, value::text AS value FROM core.network_setting ORDER BY key",
                    row -> values.put(row.getString("key"), row.getString("value")));
            return Map.copyOf(values);
        });
    }
}
