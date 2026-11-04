package net.retromc.bedwars;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item-Abklingzeiten je Spieler (Spec 04 §6.1.8: Rettungsplattform 20 s, Portal 10 s, Freezer 15 s, Windkanone 2 s;
 * Meldung {@code bw.item.cooldown}).
 */
public final class Cooldowns {

    private final Map<String, Instant> until = new ConcurrentHashMap<>();

    /** Restsekunden (aufgerundet) oder 0, wenn frei. */
    public long remaining(UUID player, String item, Instant now) {
        Instant end = until.get(player + ":" + item);
        if (end == null || !now.isBefore(end)) {
            return 0;
        }
        long millis = Duration.between(now, end).toMillis();
        return (millis + 999) / 1000;
    }

    /** Startet die Abklingzeit. */
    public void start(UUID player, String item, Duration duration, Instant now) {
        if (!duration.isZero() && !duration.isNegative()) {
            until.put(player + ":" + item, now.plus(duration));
        }
    }
}
