package net.retromc.core.api.stats;

import java.util.Objects;
import java.util.UUID;

/**
 * Eintrag einer Bestenliste (Cache {@code top:{mode}:{period}}, Spec 02 §2.2): für Schilder, Hologramme und
 * {@code /top}. Versteckte Spieler erscheinen mit Namen (Spec 02 §4.9).
 *
 * @param position Platz ab 1
 * @param name letzter bekannter Name
 * @param color Rangfarbe als §-Code
 * @param score Ranking-Score ({@code lb:{mode}:{period}})
 */
public record TopEntry(int position, UUID uuid, String name, String color, double score) {

    public TopEntry {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(color, "color");
        if (position < 1) {
            throw new IllegalArgumentException("Platz ab 1: " + position);
        }
    }
}
