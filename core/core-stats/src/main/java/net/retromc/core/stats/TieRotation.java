package net.retromc.core.stats;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.retromc.core.api.stats.TopEntry;

/**
 * Gleichstand auf Wänden und Hologrammen (Spec 02 §4.9): Spieler mit gleichem Ranking-Score wechseln sich auf ihren
 * Plätzen alle 5 s ab. Dafür wird jede Gruppe gleicher Scores im Takt um eine Stelle weitergedreht; die Platznummern
 * bleiben stehen. Die Liste sollte über die sichtbare Größe hinausreichen, damit Gruppen an der Grenze rotieren.
 */
public final class TieRotation {

    /** Takt des Wechsels. */
    public static final Duration PERIOD = Duration.ofSeconds(5);

    private TieRotation() {
    }

    /**
     * Liefert die ersten {@code size} Einträge im Takt {@code step} ({@code epochSekunden / 5}).
     *
     * @param entries nach Platz sortiert
     */
    public static List<TopEntry> rotate(List<TopEntry> entries, long step, int size) {
        List<TopEntry> result = new ArrayList<>(entries.size());
        int start = 0;
        while (start < entries.size()) {
            int end = start + 1;
            while (end < entries.size() && Double.compare(entries.get(end).score(), entries.get(start).score()) == 0) {
                end++;
            }
            int group = end - start;
            int shift = (int) Math.floorMod(step, (long) group);
            for (int i = 0; i < group; i++) {
                TopEntry slot = entries.get(start + i);
                TopEntry shown = entries.get(start + (i + shift) % group);
                result.add(new TopEntry(slot.position(), shown.uuid(), shown.name(), shown.color(), shown.score()));
            }
            start = end;
        }
        return List.copyOf(result.subList(0, Math.min(size, result.size())));
    }
}
