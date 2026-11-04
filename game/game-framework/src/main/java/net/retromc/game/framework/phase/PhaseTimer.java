package net.retromc.game.framework.phase;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Sekunden-Timer einer Phase (Spec 03 §8.1 {@code PhaseTimer}, einzige Timer-API der Modi: Countdown, Respawn,
 * Generatorstufen, Restart). {@code tick} erhält den Rest direkt beim Start und dann jede Sekunde, bei 0 folgt
 * {@code done}. Läuft auf dem Haupt-Thread.
 */
public final class PhaseTimer {

    private final Plugin plugin;
    private @Nullable BukkitTask task;
    private String id = "";
    private int remaining;
    private int total;
    private boolean held;
    private IntConsumer tick = rest -> { };
    private Runnable done = () -> { };
    private IntConsumer announce = rest -> { };
    private final Set<Integer> announceAt = new HashSet<>();

    public PhaseTimer(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Startet (ein laufender Timer wird ersetzt). */
    public PhaseTimer start(String timerId, Duration duration, IntConsumer onTick, Runnable onDone) {
        cancel();
        this.id = Objects.requireNonNull(timerId, "timerId");
        this.total = (int) Math.max(0, duration.toSeconds());
        this.remaining = total;
        this.tick = Objects.requireNonNull(onTick, "onTick");
        this.done = Objects.requireNonNull(onDone, "onDone");
        this.held = false;
        this.announceAt.clear();
        this.announce = rest -> { };
        fire();
        if (remaining == 0) {
            finish();
            return this;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        return this;
    }

    /** Ruft {@code onAnnounce} bei den angegebenen Restsekunden auf (zusätzlich zu {@code tick}). */
    public PhaseTimer announceAt(IntConsumer onAnnounce, int... seconds) {
        this.announce = Objects.requireNonNull(onAnnounce, "onAnnounce");
        for (int second : seconds) {
            announceAt.add(second);
        }
        if (announceAt.contains(remaining) && running()) {
            announce.accept(remaining);
        }
        return this;
    }

    /** Setzt den Rest (z. B. {@code /start} → 10 s). */
    public void set(int seconds) {
        remaining = Math.max(0, seconds);
    }

    /** Hält den Timer an der aktuellen Sekunde an (Map lädt noch) bzw. lässt ihn weiterlaufen. */
    public void hold(boolean value) {
        held = value;
    }

    public int remaining() {
        return remaining;
    }

    public int total() {
        return total;
    }

    public String id() {
        return id;
    }

    public boolean running() {
        return task != null;
    }

    public void cancel() {
        BukkitTask current = task;
        if (current != null) {
            current.cancel();
        }
        task = null;
    }

    private void second() {
        if (held) {
            return;
        }
        remaining = Math.max(0, remaining - 1);
        fire();
        if (remaining == 0) {
            finish();
        }
    }

    private void fire() {
        tick.accept(remaining);
        if (announceAt.contains(remaining)) {
            announce.accept(remaining);
        }
    }

    private void finish() {
        cancel();
        done.run();
    }
}
