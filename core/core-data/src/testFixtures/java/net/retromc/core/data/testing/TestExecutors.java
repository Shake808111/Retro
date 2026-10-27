package net.retromc.core.data.testing;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import net.retromc.core.data.AsyncDatabase;

/**
 * Async-Executor für Tests: ein benannter Einzel-Thread (deterministische Reihenfolge) für {@link AsyncDatabase} und
 * {@link #await} mit festem Zeitlimit, damit hängende Zukünfte einen Test nicht blockieren.
 */
public final class TestExecutors {

    /** Standard-Zeitlimit für {@link #await(CompletableFuture)}. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private TestExecutors() {
    }

    /** Einzel-Thread-Executor ({@code test-db}); der Aufrufer beendet ihn. */
    public static ExecutorService singleThread() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("test-db").daemon(true).factory());
    }

    /** {@link AsyncDatabase} mit eigenem virtuellen Executor, der beim Schließen endet. */
    public static AsyncDatabase asyncDatabase(DataSource dataSource) {
        return new AsyncDatabase(dataSource);
    }

    /** Wartet höchstens {@link #DEFAULT_TIMEOUT} auf das Ergebnis. */
    public static <T> T await(CompletableFuture<T> future) {
        return await(future, DEFAULT_TIMEOUT);
    }

    /**
     * Wartet auf das Ergebnis; die Ursache eines Fehlers wird ausgepackt und als {@link RuntimeException} geworfen.
     */
    public static <T> T await(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Zeitlimit " + timeout + " überschritten", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Warten unterbrochen", e);
        }
    }
}
