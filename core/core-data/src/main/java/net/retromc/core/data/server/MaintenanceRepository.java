package net.retromc.core.data.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.server.MaintenanceService;
import net.retromc.core.api.server.MaintenanceState;
import net.retromc.core.api.server.NetworkSettingService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * {@link MaintenanceService}: Status in {@code core.network_setting['maintenance']} (JSON
 * {@code {"enabled":…,"reason":…,"since":ms}}) und Valkey-HASH {@code maintenance} ({@code enabled, reason, since}),
 * Whitelist {@code core.maintenance_whitelist} (Spec 01 §2.2, §2.3, §6.15). Sendet {@code MAINTENANCE_CHANGED}.
 */
public final class MaintenanceRepository implements MaintenanceService {

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final EventSink events;
    private final Clock clock;

    public MaintenanceRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey, EventSink events,
            Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<MaintenanceState> state() {
        return Jdbc.future(valkey.hgetall(ValkeyKeys.maintenance())).thenCompose(hash -> {
            if (hash != null && hash.containsKey("enabled")) {
                return CompletableFuture.completedFuture(fromHash(hash));
            }
            return database.query(connection -> Jdbc.one(connection, "SELECT value::text AS value FROM"
                    + " core.network_setting WHERE key = ?", row -> row.getString("value"),
                    NetworkSettingService.MAINTENANCE)).thenCompose(json -> {
                        MaintenanceState state = json.map(MaintenanceRepository::fromJson).orElse(MaintenanceState.OFF);
                        return cache(state).thenApply(ignored -> state);
                    });
        });
    }

    @Override
    public CompletableFuture<MaintenanceState> enable(@Nullable UUID actor, @Nullable String reason) {
        return store(new MaintenanceState(true, reason, clock.instant()));
    }

    @Override
    public CompletableFuture<MaintenanceState> disable(@Nullable UUID actor) {
        return store(MaintenanceState.OFF);
    }

    private CompletableFuture<MaintenanceState> store(MaintenanceState state) {
        return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO core.network_setting"
                + " (key, value, updated_at) VALUES (?, ?::jsonb, now()) ON CONFLICT (key) DO UPDATE"
                + " SET value = EXCLUDED.value, updated_at = now()", NetworkSettingService.MAINTENANCE, toJson(state)))
                .thenCompose(ignored -> cache(state))
                .thenApply(ignored -> {
                    events.publish(new MaintenanceChanged(state.enabled()));
                    return state;
                });
    }

    private CompletableFuture<Void> cache(MaintenanceState state) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("enabled", state.enabled() ? "1" : "0");
        hash.put("reason", state.reason() == null ? "" : state.reason());
        hash.put("since", state.since() == null ? "" : Long.toString(state.since().toEpochMilli()));
        return Jdbc.future(valkey.hset(ValkeyKeys.maintenance(), hash)).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Boolean> isWhitelisted(UUID player) {
        return database.query(connection -> Jdbc.count(connection,
                "SELECT count(*) FROM core.maintenance_whitelist WHERE uuid = ?", player) > 0);
    }

    @Override
    public CompletableFuture<Boolean> addToWhitelist(UUID player, @Nullable UUID addedBy) {
        return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO core.maintenance_whitelist"
                + " (uuid, added_by, added_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", player, addedBy,
                clock.instant()) > 0);
    }

    @Override
    public CompletableFuture<Boolean> removeFromWhitelist(UUID player) {
        return database.transaction(connection -> Jdbc.update(connection,
                "DELETE FROM core.maintenance_whitelist WHERE uuid = ?", player) > 0);
    }

    @Override
    public CompletableFuture<List<UUID>> whitelist() {
        return database.query(connection -> Jdbc.list(connection, "SELECT uuid FROM core.maintenance_whitelist"
                + " ORDER BY added_at, uuid", row -> Jdbc.requireUuid(row, "uuid")));
    }

    static MaintenanceState fromHash(Map<String, String> hash) {
        boolean enabled = "1".equals(hash.get("enabled"));
        String reason = hash.get("reason");
        String since = hash.get("since");
        Instant at = since == null || since.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(since));
        if (enabled && at == null) {
            at = Instant.EPOCH;
        }
        return new MaintenanceState(enabled, reason == null || reason.isEmpty() ? null : reason, enabled ? at : null);
    }

    static MaintenanceState fromJson(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            boolean enabled = object.has("enabled") && object.get("enabled").getAsBoolean();
            if (!enabled) {
                return MaintenanceState.OFF;
            }
            String reason = object.has("reason") && !object.get("reason").isJsonNull()
                    ? object.get("reason").getAsString() : null;
            Instant since = object.has("since") && !object.get("since").isJsonNull()
                    ? Instant.ofEpochMilli(object.get("since").getAsLong()) : Instant.EPOCH;
            return new MaintenanceState(true, reason, since);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException
                | NumberFormatException e) {
            return MaintenanceState.OFF;
        }
    }

    static String toJson(MaintenanceState state) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", state.enabled());
        if (state.reason() != null) {
            object.addProperty("reason", state.reason());
        }
        if (state.since() != null) {
            object.addProperty("since", state.since().toEpochMilli());
        }
        return object.toString();
    }
}
