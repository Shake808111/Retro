package net.retromc.core.data.games;

import java.sql.Array;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.games.SkyWarsStore;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/** Standard-{@link SkyWarsStore} auf {@code games.sw_kit_settings}. */
public final class SkyWarsRepository implements SkyWarsStore {

    private final AsyncDatabase database;

    public SkyWarsRepository(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<KitSettings> kitSettings(UUID player) {
        return database.query(connection -> Jdbc.one(connection,
                "SELECT legacy_enabled, hidden_kits FROM games.sw_kit_settings WHERE uuid = ?",
                row -> new KitSettings(row.getBoolean("legacy_enabled"), kits(row.getArray("hidden_kits"))), player)
                .orElse(KitSettings.DEFAULT));
    }

    @Override
    public CompletableFuture<Void> saveKitSettings(UUID player, KitSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return database.transaction(connection -> {
            Array hidden = connection.createArrayOf("varchar", new TreeSet<>(settings.hiddenKits()).toArray());
            Jdbc.update(connection, """
                    INSERT INTO games.sw_kit_settings (uuid, legacy_enabled, hidden_kits) VALUES (?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET legacy_enabled = EXCLUDED.legacy_enabled,
                        hidden_kits = EXCLUDED.hidden_kits""", player, settings.legacyEnabled(), hidden);
            return null;
        });
    }

    private static Set<String> kits(Array array) throws java.sql.SQLException {
        Set<String> result = new TreeSet<>();
        for (Object value : (Object[]) array.getArray()) {
            result.add(String.valueOf(value));
        }
        return result;
    }
}
