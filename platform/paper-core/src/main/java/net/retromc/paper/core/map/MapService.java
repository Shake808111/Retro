package net.retromc.paper.core.map;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.map.MapMeta;
import org.bukkit.World;

/**
 * Einziger Map-Lader auf Paper (Spec 08 §6.11 Nr. 6, §8.1 {@code MapService}). Maps sind fertige Welten mit einer
 * {@code map.json} im Map-Ordner; {@link #prepare} kopiert die Welt in den Welt-Container und lädt sie ohne
 * Speichern (eine Runde pro Server, die Welt wird nie zurückgeschrieben). Austauschbar über die Service-Registry
 * (z. B. später mit dem Objektspeicher {@code MapStore}).
 */
public interface MapService {

    /**
     * Auswahl (Spec 08 §6.11 Nr. 7): gültige Maps des Modus mit passender Variante und
     * {@code minPlayers ≤ players ≤ maxPlayers}.
     */
    List<MapMeta> candidates(String mode, String variant, int players);

    /** Alle gültigen Maps eines Modus. */
    List<MapMeta> maps(String mode);

    Optional<MapMeta> meta(String mapId);

    /** Kopiert und lädt die Welt der Map; die Zukunft endet auf dem Haupt-Thread. */
    CompletableFuture<World> prepare(String mapId);

    /**
     * Lädt eine fertige Welt ohne {@code map.json} (z. B. eine eigene Wartelobby-Welt) aus dem Map-Ordner bzw. dem
     * Welt-Container.
     */
    CompletableFuture<World> prepareWorld(String folder);

    /** Ordner mit den Map-Welten ({@code NETWORK_MAP_DIR}, Standard {@code maps/} im Server-Ordner). */
    Path directory();

    /** Liest die Map-Ordner neu ein (nach {@code /…setup save}). */
    void reload();
}
