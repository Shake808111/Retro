package net.retromc.bedwars.upgrade;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgrade-Stände eines Teams (Spec 04 §6.1.7) und die Wirkung der Schwächefalle: ein ausgelöster Eindringling hat
 * 60 s lang alle Upgrades seines Teams um ein Level niedriger (U6).
 */
public final class TeamUpgrades {

    /** Dauer der Schwächung. */
    public static final Duration WEAKNESS = Duration.ofSeconds(60);

    private final Map<UpgradeType, Integer> levels = new EnumMap<>(UpgradeType.class);
    private final Map<UUID, Instant> weakened = new ConcurrentHashMap<>();

    public synchronized int level(UpgradeType type) {
        return levels.getOrDefault(type, 0);
    }

    /** Level, das {@code player} gerade wirkt (Schwächefalle: −1, nie unter 0). */
    public int effective(UpgradeType type, UUID player, Instant now) {
        int level = level(type);
        Instant until = weakened.get(player);
        return until != null && now.isBefore(until) ? Math.max(0, level - 1) : level;
    }

    /** Ob ein weiteres Level gekauft werden kann. */
    public synchronized boolean maxed(UpgradeType type) {
        return level(type) >= type.maxLevel();
    }

    /** Preis des nächsten Levels (Fehler, wenn schon Maximum). */
    public synchronized int nextCost(UpgradeType type) {
        return type.cost(level(type) + 1);
    }

    /** Erhöht um ein Level und liefert das neue Level. */
    public synchronized int raise(UpgradeType type) {
        if (maxed(type)) {
            throw new IllegalStateException(type + " ist bereits auf dem höchsten Level");
        }
        int next = level(type) + 1;
        levels.put(type, next);
        return next;
    }

    /** Schwächt {@code player} ab {@code now} für 60 s. */
    public void weaken(UUID player, Instant now) {
        weakened.put(player, now.plus(WEAKNESS));
    }

    /** Ob {@code player} gerade geschwächt ist. */
    public boolean weakened(UUID player, Instant now) {
        Instant until = weakened.get(player);
        return until != null && now.isBefore(until);
    }
}
