package net.retromc.platform.common.connect;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Wiederholt einen Verbindungsaufbau (Spec 08 §6.2 Nr. 2: 3 Versuche im Abstand von 2 s, danach Abbruch).
 */
public final class Retry {

    /** Vorgabe der Kern-Plugins beim Start. */
    public static final Retry STARTUP = new Retry(3, Duration.ofSeconds(2), duration -> Thread.sleep(duration));

    /** Wartet zwischen zwei Versuchen (in Tests ersetzbar). */
    @FunctionalInterface
    public interface Sleeper {

        void sleep(Duration duration) throws InterruptedException;
    }

    /** Ein Versuch, der ein Ergebnis liefert. */
    @FunctionalInterface
    public interface Attempt<T> {

        T run() throws Exception;
    }

    /** Ein Versuch ohne Ergebnis. */
    @FunctionalInterface
    public interface Action {

        void run() throws Exception;
    }

    private final int attempts;
    private final Duration delay;
    private final Sleeper sleeper;

    public Retry(int attempts, Duration delay, Sleeper sleeper) {
        if (attempts < 1) {
            throw new IllegalArgumentException("Mindestens ein Versuch nötig: " + attempts);
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Wartezeit darf nicht negativ sein: " + delay);
        }
        this.attempts = attempts;
        this.delay = delay;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public int attempts() {
        return attempts;
    }

    public Duration delay() {
        return delay;
    }

    /**
     * Führt {@code attempt} aus, bis er gelingt oder alle Versuche verbraucht sind.
     *
     * @param what Ziel für Log und Fehlermeldung, z. B. {@code PostgreSQL}
     * @throws ConnectionException wenn alle Versuche scheitern oder das Warten unterbrochen wird
     */
    public <T> T call(String what, Logger log, Attempt<T> attempt) {
        @Nullable Exception last = null;
        for (int number = 1; number <= attempts; number++) {
            try {
                return attempt.run();
            } catch (Exception e) {
                last = e;
                if (number < attempts) {
                    log.warn("{}: Versuch {}/{} fehlgeschlagen ({}), neuer Versuch in {} ms", what, number, attempts,
                            e.getMessage(), delay.toMillis());
                    pause(what);
                }
            }
        }
        throw new ConnectionException(what + " nicht erreichbar nach " + attempts + " Versuchen", last);
    }

    /** Wie {@link #call}, ohne Ergebnis. */
    public void run(String what, Logger log, Action action) {
        call(what, log, () -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private void pause(String what) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(what + ": Warten auf neuen Versuch unterbrochen", e);
        }
    }
}
