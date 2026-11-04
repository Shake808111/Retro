package net.retromc.bedwars.trap;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Fallen-Warteschlange eines Teams (Spec 04 §6.1.7, U6): höchstens 3 Fallen, eine Falle löst pro Eindringen aus,
 * danach 30 s Sperre.
 */
public final class TrapQueue {

    /** Plätze in der Warteschlange. */
    public static final int CAPACITY = 3;
    /** Sperre nach dem Auslösen. */
    public static final Duration LOCK = Duration.ofSeconds(30);

    private final Deque<TrapType> queue = new ArrayDeque<>();
    private Instant lockedUntil = Instant.MIN;

    /** Fügt eine Falle hinzu; {@code false}, wenn die Warteschlange voll ist. */
    public synchronized boolean add(TrapType trap) {
        if (queue.size() >= CAPACITY) {
            return false;
        }
        queue.addLast(trap);
        return true;
    }

    public synchronized boolean full() {
        return queue.size() >= CAPACITY;
    }

    public synchronized List<TrapType> traps() {
        return List.copyOf(queue);
    }

    /** Ob die nächste Falle bei {@code now} auslösen darf (nicht leer, keine Sperre). */
    public synchronized boolean armed(Instant now) {
        return !queue.isEmpty() && !now.isBefore(lockedUntil);
    }

    /** Löst die erste Falle aus und sperrt 30 s; leer, wenn keine scharfe Falle da ist. */
    public synchronized Optional<TrapType> trigger(Instant now) {
        if (!armed(now)) {
            return Optional.empty();
        }
        lockedUntil = now.plus(LOCK);
        return Optional.of(queue.pollFirst());
    }

    /** Radius der nächsten Falle (0, wenn leer). */
    public synchronized int nextRadius() {
        TrapType next = queue.peekFirst();
        return next == null ? 0 : next.radius();
    }
}
