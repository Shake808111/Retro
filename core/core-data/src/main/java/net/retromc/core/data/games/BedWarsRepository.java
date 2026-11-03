package net.retromc.core.data.games;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/** Standard-{@link BedWarsStore} auf {@code games.bw_quickshop}, {@code bw_shop_settings}, {@code bw_goldvote}. */
public final class BedWarsRepository implements BedWarsStore {

    private static final Gson GSON = new Gson();
    private static final List<String> RATES = List.of("none", "low", "normal", "high");

    private final AsyncDatabase database;

    public BedWarsRepository(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<Map<Integer, String>> quickshop(UUID player, String group) {
        BedWarsStore.checkGroup(group);
        return database.query(connection -> {
            Map<Integer, String> slots = new TreeMap<>();
            Jdbc.list(connection, "SELECT slot, item_id FROM games.bw_quickshop WHERE uuid = ? AND variant_group = ?",
                    row -> Map.entry((int) row.getShort("slot"), row.getString("item_id")), player, group)
                    .forEach(entry -> slots.put(entry.getKey(), entry.getValue()));
            return Map.copyOf(slots);
        });
    }

    @Override
    public CompletableFuture<Void> setQuickshopSlot(UUID player, String group, int slot, String itemId) {
        BedWarsStore.checkGroup(group);
        BedWarsStore.checkSlot(slot);
        Objects.requireNonNull(itemId, "itemId");
        return database.transaction(connection -> {
            Jdbc.update(connection, """
                    INSERT INTO games.bw_quickshop (uuid, variant_group, slot, item_id) VALUES (?, ?, ?, ?)
                    ON CONFLICT (uuid, variant_group, slot) DO UPDATE SET item_id = EXCLUDED.item_id""",
                    player, group, (short) slot, itemId);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> clearQuickshopSlot(UUID player, String group, int slot) {
        BedWarsStore.checkGroup(group);
        BedWarsStore.checkSlot(slot);
        return database.transaction(connection -> {
            Jdbc.update(connection, "DELETE FROM games.bw_quickshop WHERE uuid = ? AND variant_group = ? AND slot = ?",
                    player, group, (short) slot);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveQuickshop(UUID player, String group, Map<Integer, String> slots) {
        BedWarsStore.checkGroup(group);
        slots.keySet().forEach(BedWarsStore::checkSlot);
        Map<Integer, String> copy = new TreeMap<>(slots);
        return database.transaction(connection -> {
            Jdbc.update(connection, "DELETE FROM games.bw_quickshop WHERE uuid = ? AND variant_group = ?", player,
                    group);
            for (Map.Entry<Integer, String> entry : copy.entrySet()) {
                Jdbc.update(connection, "INSERT INTO games.bw_quickshop (uuid, variant_group, slot, item_id) "
                        + "VALUES (?, ?, ?, ?)", player, group, entry.getKey().shortValue(), entry.getValue());
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<ShopSettings> shopSettings(UUID player) {
        return database.query(connection -> Jdbc.one(connection,
                "SELECT category_order, hotbar_pref::text AS hotbar FROM games.bw_shop_settings WHERE uuid = ?",
                row -> new ShopSettings(order(row.getArray("category_order")), hotbar(row.getString("hotbar"))),
                player).orElse(ShopSettings.DEFAULT));
    }

    @Override
    public CompletableFuture<Void> saveShopSettings(UUID player, ShopSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return database.transaction(connection -> {
            Jdbc.update(connection, """
                    INSERT INTO games.bw_shop_settings (uuid, category_order, hotbar_pref, updated_at)
                    VALUES (?, ?, ?::jsonb, now())
                    ON CONFLICT (uuid) DO UPDATE SET category_order = EXCLUDED.category_order,
                        hotbar_pref = EXCLUDED.hotbar_pref, updated_at = now()""",
                    player, array(connection, settings.categoryOrder()), GSON.toJson(settings.hotbar()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<String>> goldVote(UUID player) {
        return database.query(connection -> Jdbc.one(connection,
                "SELECT rate FROM games.bw_goldvote WHERE uuid = ?", row -> row.getString("rate"), player));
    }

    @Override
    public CompletableFuture<Void> saveGoldVote(UUID player, String rate) {
        if (!RATES.contains(rate)) {
            throw new IllegalArgumentException("Gold-Wahl none/low/normal/high: " + rate);
        }
        return database.transaction(connection -> {
            Jdbc.update(connection, """
                    INSERT INTO games.bw_goldvote (uuid, rate, saved_at) VALUES (?, ?, now())
                    ON CONFLICT (uuid) DO UPDATE SET rate = EXCLUDED.rate, saved_at = now()""", player, rate);
            return null;
        });
    }

    private static Array array(Connection connection, List<Integer> values) throws SQLException {
        return connection.createArrayOf("smallint", values.stream().map(Integer::shortValue).toArray());
    }

    private static List<Integer> order(Array array) throws SQLException {
        List<Integer> result = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            result.add(((Number) value).intValue());
        }
        return result;
    }

    private static Map<String, Integer> hotbar(String json) {
        Map<String, Integer> result = new LinkedHashMap<>();
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsInt()));
        return result;
    }
}
