package net.retromc.core.api.games;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persönliche SkyWars-Daten ({@code games.sw_kit_settings}, Spec 04 §2.1, GUI §5.10 „Kit-Einstellungen“). */
public interface SkyWarsStore {

    /**
     * Kit-Einstellungen.
     *
     * @param legacyEnabled frühere Kits wieder anzeigen
     * @param hiddenKits    ausgeblendete Kit-IDs
     */
    record KitSettings(boolean legacyEnabled, Set<String> hiddenKits) {

        /** Standard: frühere Kits aus, nichts ausgeblendet. */
        public static final KitSettings DEFAULT = new KitSettings(false, Set.of());

        public KitSettings {
            hiddenKits = Set.copyOf(hiddenKits);
            hiddenKits.forEach(id -> {
                if (id.isEmpty() || id.length() > 32) {
                    throw new IllegalArgumentException("Kit-ID 1–32 Zeichen: " + id);
                }
            });
        }

        public KitSettings withLegacy(boolean value) {
            return new KitSettings(value, hiddenKits);
        }

        /** Blendet {@code kitId} aus bzw. wieder ein. */
        public KitSettings toggleHidden(String kitId) {
            Set<String> next = new TreeSet<>(hiddenKits);
            if (!next.remove(kitId)) {
                next.add(kitId);
            }
            return new KitSettings(legacyEnabled, next);
        }
    }

    CompletableFuture<KitSettings> kitSettings(UUID player);

    CompletableFuture<Void> saveKitSettings(UUID player, KitSettings settings);
}
