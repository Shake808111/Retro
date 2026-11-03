package net.retromc.paper.core.bar;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.retromc.paper.core.bar.ActionbarService.Priority;

/**
 * Actionbar-Kanäle eines Spielers ohne Server (Spec 02 §4.6): angezeigt wird der Kanal mit der höchsten Priorität;
 * bei gleicher Priorität der zuletzt gesetzte. Abgelaufene {@code temporary}-Texte fallen weg.
 */
public final class ActionbarQueue {

    private record Entry(Priority priority, Component text, long expiresAt, long order) {
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private long counter;

    /**
     * @param now aktueller Tick
     * @param durationTicks 0 = ohne Ablauf
     */
    public void set(String channel, Priority priority, Component text, long now, int durationTicks) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(text, "text");
        entries.put(channel, new Entry(priority, text, durationTicks > 0 ? now + durationTicks : Long.MAX_VALUE,
                ++counter));
    }

    public void clear(String channel) {
        entries.remove(channel);
    }

    /** Aktueller Text zum Tick {@code now}; entfernt Abgelaufenes. */
    public Optional<Component> current(long now) {
        entries.values().removeIf(entry -> entry.expiresAt() <= now);
        return entries.values().stream()
                .min(Comparator.comparing(Entry::priority).thenComparing(Comparator.comparingLong(Entry::order)
                        .reversed()))
                .map(Entry::text);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
