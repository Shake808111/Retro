package net.retromc.core.data.stats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.stats.AnchorType;
import net.retromc.core.api.stats.DisplayAnchor;
import net.retromc.core.api.stats.DisplayAnchorStore;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/**
 * Standard-{@link DisplayAnchorStore} auf {@code core.display_anchor} (Spec 02 §2.1). Der Modus steht als
 * {@code mode_id} in der Tabelle und wird über {@code stats.mode} auf den Schlüssel abgebildet.
 */
public final class DisplayAnchorRepository implements DisplayAnchorStore {

    private static final String COLUMNS = "a.id, a.server_group, a.world, a.type, m.key AS mode, a.period, a.size,"
            + " a.x, a.y, a.z, a.yaw, a.facing, a.created_by";

    private final AsyncDatabase database;

    public DisplayAnchorRepository(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<DisplayAnchor> create(DisplayAnchor anchor) {
        return database.transaction(connection -> Jdbc.one(connection, "INSERT INTO core.display_anchor"
                + " (server_group, world, type, mode_id, period, size, x, y, z, yaw, facing, created_by)"
                + " SELECT ?, ?, ?, m.id, ?, ?, ?, ?, ?, ?, ?, ? FROM stats.mode m WHERE m.key = ? RETURNING id",
                row -> row.getLong("id"), anchor.serverGroup(), anchor.world(), anchor.type().name(),
                anchor.period().key(), (short) anchor.size(), anchor.x(), anchor.y(), anchor.z(), anchor.yaw(),
                anchor.facing(), anchor.createdBy(), anchor.mode().key())
                .map(anchor::withId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Spielmodus: " + anchor.mode())));
    }

    @Override
    public CompletableFuture<List<DisplayAnchor>> list(String serverGroup) {
        return database.query(connection -> Jdbc.list(connection, "SELECT " + COLUMNS + " FROM core.display_anchor a"
                + " JOIN stats.mode m ON m.id = a.mode_id WHERE a.server_group = ? ORDER BY a.id",
                DisplayAnchorRepository::anchor, serverGroup));
    }

    @Override
    public CompletableFuture<Boolean> delete(long id) {
        return database.transaction(connection -> Jdbc.update(connection,
                "DELETE FROM core.display_anchor WHERE id = ?", id) > 0);
    }

    private static DisplayAnchor anchor(ResultSet row) throws SQLException {
        return new DisplayAnchor(row.getLong("id"), row.getString("server_group"), row.getString("world"),
                AnchorType.valueOf(row.getString("type")), ModeKey.of(row.getString("mode")),
                StatPeriod.byKey(row.getString("period")).orElse(StatPeriod.DAYS_30), row.getShort("size"),
                row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"),
                row.getString("facing"), Jdbc.uuid(row, "created_by"));
    }
}
