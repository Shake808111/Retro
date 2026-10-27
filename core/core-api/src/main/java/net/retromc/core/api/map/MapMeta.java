package net.retromc.core.api.map;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Inhalt von {@code map.json} (Spec 08 §6.11), Rückgabe von {@code MapService.prepare/meta} und Spalte
 * {@code infra.map_version.meta}. Die modusspezifischen {@code markers} bleiben als JSON-Text, weil jeder Modus sie
 * gegen sein eigenes {@code mapschema/<mode>.json} liest.
 *
 * @param variants Varianten-IDs; leer = alle Varianten
 * @param license muss {@code "own"} sein (Spec 08 §6.19), geprüft vom Map-Exporter
 * @param waitingLobby Spawn der Wartelobby, falls auf der Map, sonst {@code null}
 * @param markersJson JSON-Objekt {@code markers}
 */
public record MapMeta(
        String id,
        String displayName,
        String mode,
        List<String> variants,
        int version,
        List<String> builders,
        String license,
        int minPlayers,
        int maxPlayers,
        Bounds bounds,
        Spawn spectatorSpawn,
        @Nullable Spawn waitingLobby,
        String markersJson) {

    /** {@code ^[a-z0-9_]{2,32}$}, gleich dem Ordnernamen. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_]{2,32}");

    /** Einzige zulässige Lizenz (eigene Maps). */
    public static final String LICENSE_OWN = "own";

    public MapMeta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(license, "license");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(spectatorSpawn, "spectatorSpawn");
        Objects.requireNonNull(markersJson, "markersJson");
        variants = List.copyOf(variants);
        builders = List.copyOf(builders);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Map-ID: '" + id + "'");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version muss ≥ 1 sein");
        }
        if (minPlayers < 1 || minPlayers > maxPlayers || maxPlayers > 200) {
            throw new IllegalArgumentException("Es gilt 1 ≤ minPlayers ≤ maxPlayers ≤ 200");
        }
    }

    /** Ob die Map für die Variante gilt (leere Liste = alle). */
    public boolean supportsVariant(String variant) {
        return variants.isEmpty() || variants.contains(variant);
    }

    /** Auswahlregel {@code minPlayers ≤ Teamgröße×Teams ≤ maxPlayers} (Spec 08 §6.11 Nr. 7). */
    public boolean fits(int players) {
        return minPlayers <= players && players <= maxPlayers;
    }

    /** Blockposition. */
    public record Position(double x, double y, double z) {
    }

    /** Spawn mit Blickrichtung. */
    public record Spawn(double x, double y, double z, float yaw, float pitch) {

        public Position position() {
            return new Position(x, y, z);
        }
    }

    /** Begrenzung {@code {min:{x,y,z}, max:{x,y,z}}}, min &lt; max je Achse. */
    public record Bounds(Position min, Position max) {

        public Bounds {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            if (!(min.x() < max.x() && min.y() < max.y() && min.z() < max.z())) {
                throw new IllegalArgumentException("bounds: min muss je Achse kleiner als max sein");
            }
        }

        public boolean contains(Position position) {
            return position.x() >= min.x() && position.x() <= max.x()
                    && position.y() >= min.y() && position.y() <= max.y()
                    && position.z() >= min.z() && position.z() <= max.z();
        }
    }
}
