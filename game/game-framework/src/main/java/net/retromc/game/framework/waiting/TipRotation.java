package net.retromc.game.framework.waiting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Tipps der Wartelobby (Spec 03 §6.5 Nr. 4): zufällig ohne Wiederholung, bis die Liste durch ist; danach neu
 * gemischt, ohne denselben Tipp direkt zu wiederholen.
 *
 * @param <T> Tipp (Textschlüssel)
 */
public final class TipRotation<T> {

    private final List<T> tips;
    private final Random random;
    private final Deque<T> deck = new ArrayDeque<>();
    private T last;

    public TipRotation(List<T> tips, Random random) {
        this.tips = List.copyOf(tips);
        this.random = Objects.requireNonNull(random, "random");
        if (this.tips.isEmpty()) {
            throw new IllegalArgumentException("mindestens ein Tipp");
        }
    }

    public synchronized T next() {
        if (deck.isEmpty()) {
            List<T> shuffled = new ArrayList<>(tips);
            Collections.shuffle(shuffled, random);
            if (shuffled.size() > 1 && shuffled.getFirst().equals(last)) {
                Collections.swap(shuffled, 0, shuffled.size() - 1);
            }
            deck.addAll(shuffled);
        }
        last = deck.poll();
        return last;
    }

    public int size() {
        return tips.size();
    }
}
