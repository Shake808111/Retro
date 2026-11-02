package net.retromc.platform.common.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wiederholung nach Spec 08 §6.2 Nr. 2: 3 Versuche im Abstand von 2 s. */
class RetryTest {

    private static final Logger LOG = LoggerFactory.getLogger(RetryTest.class);

    private final List<Duration> pauses = new ArrayList<>();
    private final Retry retry = new Retry(3, Duration.ofSeconds(2), pauses::add);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void startVorgabeEntsprichtDerSpec() {
        assertEquals(3, Retry.STARTUP.attempts());
        assertEquals(Duration.ofSeconds(2), Retry.STARTUP.delay());
    }

    @Test
    void erfolgImDrittenVersuch() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.call("Dienst", LOG, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("noch nicht");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(2)), pauses);
    }

    @Test
    void scheitertNachAllenVersuchenMitLetzterUrsache() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException last = new IllegalStateException("3");

        ConnectionException error = assertThrows(ConnectionException.class, () -> retry.run("Valkey", LOG, () -> {
            if (calls.incrementAndGet() == 3) {
                throw last;
            }
            throw new IllegalStateException(String.valueOf(calls.get()));
        }));

        assertEquals(3, calls.get());
        assertEquals(2, pauses.size());
        assertSame(last, error.getCause());
        assertTrue(error.getMessage().startsWith("Valkey nicht erreichbar nach 3 Versuchen"));
    }

    @Test
    void unterbrechungBeendetSofort() {
        Retry interrupted = new Retry(3, Duration.ofSeconds(2), duration -> {
            throw new InterruptedException();
        });
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ConnectionException.class, () -> interrupted.run("PostgreSQL", LOG, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("weg");
        }));

        assertEquals(1, calls.get());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void ungueltigeParameter() {
        assertThrows(IllegalArgumentException.class, () -> new Retry(0, Duration.ZERO, pauses::add));
        assertThrows(IllegalArgumentException.class, () -> new Retry(1, Duration.ofSeconds(-1), pauses::add));
    }
}
