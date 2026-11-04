package net.retromc.core.data.nick;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Rate-Limit-Schonung für Mojang (Namens-API und Sessionserver erlauben grob 600 Anfragen je 10 Minuten und IP):
 * zwischen zwei Anfragen liegt mindestens {@code minInterval}; nach HTTP 429 ruht der Abruf bis {@code Retry-After}
 * (Standard 60 s). Gilt für alle Anfragen einer Instanz, auch über mehrere {@code /nickpool fill} hinweg.
 */
public final class RequestPacer {

    /** Wartezeit nach HTTP 429 ohne {@code Retry-After}. */
    public static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(60);

    /** Wartet blockierend, produktiv {@link Thread#sleep(Duration)}. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final Duration minInterval;
    private final Clock clock;
    private final Sleeper sleeper;
    private Instant next = Instant.MIN;

    public RequestPacer(Duration minInterval, Clock clock, Sleeper sleeper) {
        if (minInterval.isNegative()) {
            throw new IllegalArgumentException("Abstand negativ: " + minInterval);
        }
        this.minInterval = minInterval;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Wartet bis zur nächsten erlaubten Anfrage und reserviert den Platz danach. */
    public synchronized void await() throws InterruptedException {
        Instant now = clock.instant();
        if (now.isBefore(next)) {
            sleeper.sleep(Duration.between(now, next));
            Instant after = clock.instant();
            now = after.isBefore(next) ? next : after;
        }
        next = now.plus(minInterval);
    }

    /** Nach HTTP 429: keine Anfrage vor Ablauf von {@code retryAfter} (leer bzw. negativ: 60 s). */
    public synchronized void backoff(Duration retryAfter) {
        Duration wait = retryAfter.isNegative() || retryAfter.isZero() ? DEFAULT_BACKOFF : retryAfter;
        Instant until = clock.instant().plus(wait);
        if (until.isAfter(next)) {
            next = until;
        }
    }

    /** Ob gerade eine Sperre nach 429 läuft (Rest länger als der normale Abstand). */
    public synchronized boolean coolingDown() {
        return Duration.between(clock.instant(), next).compareTo(minInterval) > 0;
    }
}
