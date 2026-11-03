package net.retromc.core.api.stats;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Position einer Bestenlisten-Wand oder eines Hologramms ({@code core.display_anchor}, Spec 02 §2.1, §3
 * {@code /statswall}).
 *
 * @param id {@code BIGSERIAL}; 0 = noch nicht gespeichert
 * @param serverGroup Template der Server, die den Anker zeigen (z. B. {@code lobby-bedwars})
 * @param size Anzahl der Plätze (Standard 10)
 * @param facing Blickrichtung der Schilderwand ({@code NORTH} …) oder {@code null}
 */
public record DisplayAnchor(
        long id,
        String serverGroup,
        String world,
        AnchorType type,
        ModeKey mode,
        StatPeriod period,
        int size,
        double x,
        double y,
        double z,
        float yaw,
        @Nullable String facing,
        @Nullable UUID createdBy) {

    /** Standardgröße (Top 10, Spec 02 §6.14). */
    public static final int DEFAULT_SIZE = 10;

    public DisplayAnchor {
        Objects.requireNonNull(serverGroup, "serverGroup");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(period, "period");
        if (size < 1 || size > DEFAULT_SIZE) {
            throw new IllegalArgumentException("Größe 1–" + DEFAULT_SIZE + ": " + size);
        }
    }

    /** Derselbe Anker mit vergebener ID. */
    public DisplayAnchor withId(long newId) {
        return new DisplayAnchor(newId, serverGroup, world, type, mode, period, size, x, y, z, yaw, facing,
                createdBy);
    }

    /** Quadrierter Abstand zu einem Punkt derselben Welt. */
    public double distanceSquared(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
