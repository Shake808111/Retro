package net.retromc.core.data.npc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.npc.NpcStore;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/** Standard-{@link NpcStore} auf {@code core.npc} (Migration {@code V2_3}). */
public final class NpcRepository implements NpcStore {

    private static final String COLUMNS = "id, server_group, world, x, y, z, yaw, pitch, name, skin_name, skin_value,"
            + " skin_signature, look_at, created_by";

    private final AsyncDatabase database;

    public NpcRepository(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<StoredNpc> create(StoredNpc npc) {
        Objects.requireNonNull(npc, "npc");
        return database.transaction(connection -> Jdbc.one(connection, "INSERT INTO core.npc (server_group, world,"
                + " x, y, z, yaw, pitch, name, skin_name, skin_value, skin_signature, look_at, created_by)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", row -> row.getLong("id"),
                npc.serverGroup(), npc.world(), npc.x(), npc.y(), npc.z(), npc.yaw(), npc.pitch(), npc.name(),
                npc.skinName(), npc.skinValue(), npc.skinSignature(), npc.lookAt(), npc.createdBy())
                .map(npc::withId).orElseThrow());
    }

    @Override
    public CompletableFuture<List<StoredNpc>> list(String serverGroup) {
        return database.query(connection -> Jdbc.list(connection, "SELECT " + COLUMNS
                + " FROM core.npc WHERE server_group = ? ORDER BY id", NpcRepository::npc, serverGroup));
    }

    @Override
    public CompletableFuture<Boolean> delete(long id) {
        return database.transaction(connection -> Jdbc.update(connection, "DELETE FROM core.npc WHERE id = ?",
                id) > 0);
    }

    private static StoredNpc npc(ResultSet row) throws SQLException {
        return new StoredNpc(row.getLong("id"), row.getString("server_group"), row.getString("world"),
                row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"),
                row.getFloat("pitch"), row.getString("name"), row.getString("skin_name"), row.getString("skin_value"),
                row.getString("skin_signature"), row.getBoolean("look_at"), Jdbc.uuid(row, "created_by"));
    }
}
