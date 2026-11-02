package net.retromc.paper.core.gui;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Klick-Sperre je Spieler (Spec 02 §5.1: 150 ms, Annahme), gegen Doppelausführung durch schnelles Klicken. */
public final class ClickCooldown {

    /** Sperre nach einem angenommenen Klick. */
    public static final Duration DEFAULT = Duration.ofMillis(150);

    private final long cooldownNanos;
    private final LongSupplier nanoClock;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public ClickCooldown() {
        this(DEFAULT, System::nanoTime);
    }

    /**
     * @param nanoClock monotone Uhr in Nanosekunden (Tests)
     */
    public ClickCooldown(Duration cooldown, LongSupplier nanoClock) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("Sperre darf nicht negativ sein: " + cooldown);
        }
        this.cooldownNanos = cooldown.toNanos();
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /** {@code true}, wenn der Klick angenommen wird; startet dann die Sperre neu. */
    public boolean tryClick(UUID player) {
        long now = nanoClock.getAsLong();
        Long previous = lastClick.get(player);
        if (previous != null && now - previous < cooldownNanos) {
            return false;
        }
        lastClick.put(player, now);
        return true;
    }

    /** Vergisst den Spieler (beim Verlassen des Servers). */
    public void forget(UUID player) {
        lastClick.remove(player);
    }
}
