package net.retromc.paper.core.item;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Farbvarianten von Materialnamen (Glas, Wolle, Beton, Farbstoff …), z. B. {@code WHITE_STAINED_GLASS_PANE} mit
 * {@code LIME} → {@code LIME_STAINED_GLASS_PANE}. Rein über Namen, damit ohne Server testbar.
 */
public final class DyedMaterials {

    /** Die 16 Farbstoff-Farben, längste zuerst (sonst träfe {@code BLUE} auf {@code LIGHT_BLUE_…}). */
    static final List<String> COLORS = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
                    "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK").stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private DyedMaterials() {
    }

    /** Material mit anderer Farbe, leer wenn {@code material} keine Farbvariante ist. */
    public static Optional<String> recolor(String material, String color) {
        if (!COLORS.contains(color)) {
            throw new IllegalArgumentException("Unbekannte Farbe: " + color);
        }
        for (String existing : COLORS) {
            String prefix = existing + "_";
            if (material.startsWith(prefix) && material.length() > prefix.length()) {
                return Optional.of(color + "_" + material.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    /** Glasscheibe einer Farbe, z. B. {@code LIGHT_BLUE} → {@code LIGHT_BLUE_STAINED_GLASS_PANE}. */
    public static String glassPane(String color) {
        return recolor("WHITE_STAINED_GLASS_PANE", color).orElseThrow();
    }
}
