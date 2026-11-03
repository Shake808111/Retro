package net.retromc.paper.core.holo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lage der Schilderwand ohne Server (Spec 02 §3 {@code /statswall sign}: 10 Schilder nach rechts, §4.9 Spielerkopf
 * über jedem Schild). „Rechts“ aus Sicht des Spielers, der auf die Wand schaut: Er blickt entgegen der
 * Schildrichtung.
 */
public final class WallLayout {

    private WallLayout() {
    }

    /** Ein Block relativ zum Anker. */
    public record Offset(int dx, int dy, int dz) {
    }

    /** Schritt nach rechts für ein Schild, das nach {@code facing} zeigt ({@code NORTH}, {@code EAST} …). */
    public static Offset step(String facing) {
        return switch (facing.toUpperCase(Locale.ROOT)) {
            case "NORTH" -> new Offset(-1, 0, 0);
            case "SOUTH" -> new Offset(1, 0, 0);
            case "EAST" -> new Offset(0, 0, -1);
            case "WEST" -> new Offset(0, 0, 1);
            default -> throw new IllegalArgumentException("Schildrichtung NORTH/EAST/SOUTH/WEST: " + facing);
        };
    }

    /** Schild-Positionen relativ zum Anker (Platz 1 am Anker, dann nach rechts). */
    public static List<Offset> signs(String facing, int size) {
        Offset step = step(facing);
        List<Offset> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new Offset(step.dx() * i, 0, step.dz() * i));
        }
        return List.copyOf(result);
    }

    /** Spielerkopf über dem Schild an {@code sign}. */
    public static Offset head(Offset sign) {
        return new Offset(sign.dx(), sign.dy() + 1, sign.dz());
    }
}
