package net.retromc.core.api.games;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persönliche BedWars-Daten (Spec 04 §2.1): Schnellkauf ({@code games.bw_quickshop}), Shop-Einstellungen
 * ({@code games.bw_shop_settings}) und gespeicherte Gold-Wahl ({@code games.bw_goldvote}).
 */
public interface BedWarsStore {

    /** Gruppe der Schnellkauf-Belegung: modernes BedWars. */
    String MODERN = "modern";
    /** Gruppe der Schnellkauf-Belegung: Classic. */
    String CLASSIC = "classic";
    /** Felder des Schnellkaufs (3×7-Raster). */
    int QUICKSHOP_SLOTS = 21;

    /**
     * Shop-Einstellungen.
     *
     * @param categoryOrder Reihenfolge der 9 Kategorien (Indizes 0–8)
     * @param hotbar        bevorzugter Hotbar-Slot (0–8) je Art: {@code weapon}, {@code pickaxe}, {@code blocks},
     *                      {@code bow}, {@code potion}
     */
    record ShopSettings(List<Integer> categoryOrder, Map<String, Integer> hotbar) {

        /** Standard: Kategorien 0–8, Hotbar Waffe 1, Werkzeug 2, Blöcke 4, Bogen 5, Trank 6 (Slots 0-basiert). */
        public static final ShopSettings DEFAULT = new ShopSettings(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8),
                Map.of("weapon", 0, "pickaxe", 1, "blocks", 3, "bow", 4, "potion", 5));

        public ShopSettings {
            categoryOrder = List.copyOf(categoryOrder);
            hotbar = Map.copyOf(hotbar);
            if (categoryOrder.size() != 9 || !categoryOrder.stream().sorted().toList()
                    .equals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8))) {
                throw new IllegalArgumentException("categoryOrder muss 0–8 genau einmal enthalten");
            }
            hotbar.values().forEach(slot -> {
                if (slot < 0 || slot > 8) {
                    throw new IllegalArgumentException("Hotbar-Slot 0–8: " + slot);
                }
            });
        }

        /** Hotbar-Slot einer Art (Standard, wenn nicht gesetzt). */
        public int slot(String kind) {
            return hotbar.getOrDefault(kind, DEFAULT.hotbar.getOrDefault(kind, 0));
        }
    }

    /** Schnellkauf-Belegung {@code slot → itemId}; leer = Standardbelegung des Shops. */
    CompletableFuture<Map<Integer, String>> quickshop(UUID player, String group);

    /** Setzt ({@code itemId ≠ null}) oder leert ein Schnellkauf-Feld. */
    CompletableFuture<Void> setQuickshopSlot(UUID player, String group, int slot, String itemId);

    CompletableFuture<Void> clearQuickshopSlot(UUID player, String group, int slot);

    /** Ersetzt die ganze Belegung (z. B. Standard übernehmen). */
    CompletableFuture<Void> saveQuickshop(UUID player, String group, Map<Integer, String> slots);

    CompletableFuture<ShopSettings> shopSettings(UUID player);

    CompletableFuture<Void> saveShopSettings(UUID player, ShopSettings settings);

    /** Gespeicherte Gold-Wahl ({@code none}, {@code low}, {@code normal}, {@code high}). */
    CompletableFuture<Optional<String>> goldVote(UUID player);

    CompletableFuture<Void> saveGoldVote(UUID player, String rate);

    /** Prüft einen Schnellkauf-Slot. */
    static int checkSlot(int slot) {
        if (slot < 0 || slot >= QUICKSHOP_SLOTS) {
            throw new IllegalArgumentException("Schnellkauf-Slot 0–20: " + slot);
        }
        return slot;
    }

    /** Prüft eine Gruppe. */
    static String checkGroup(String group) {
        Objects.requireNonNull(group, "group");
        if (!group.equals(MODERN) && !group.equals(CLASSIC)) {
            throw new IllegalArgumentException("Gruppe modern/classic: " + group);
        }
        return group;
    }
}
