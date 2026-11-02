package net.retromc.platform.common.concurrent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Executor für blockierende Arbeit (Datenbank, Valkey, Dateien) auf virtuellen Threads, damit der Haupt-Thread bzw.
 * die Event-Threads der Plattform nie warten (Spec 00 §4.1). Unbehandelte Fehler in {@link #execute} werden
 * protokolliert; {@link #supply} und {@link #run} liefern sie in der Zukunft.
 */
public final class AsyncExecutor implements Executor, AutoCloseable {

    /** Höchstdauer beim Schließen (Spec 08 §6.2 Nr. 8: Herunterfahren in höchstens 10 s). */
    public static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final ExecutorService delegate;
    private final Logger log;

    /**
     * @param threadPrefix Präfix der Thread-Namen, z. B. {@code retromc-async-}
     */
    public AsyncExecutor(String threadPrefix, Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        this.delegate = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name(threadPrefix, 0)
                .uncaughtExceptionHandler((thread, error) ->
                        log.error("Unbehandelter Fehler in {}", thread.getName(), error))
                .factory());
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(Objects.requireNonNull(task, "task"));
    }

    /** Führt {@code work} asynchron aus; Ergebnis oder Fehler kommen in der Zukunft an. */
    public <T> CompletableFuture<T> supply(Callable<? extends T> work) {
        Objects.requireNonNull(work, "work");
        CompletableFuture<T> future = new CompletableFuture<>();
        delegate.execute(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    /** Führt {@code work} asynchron aus. */
    public CompletableFuture<Void> run(Runnable work) {
        Objects.requireNonNull(work, "work");
        return supply(() -> {
            work.run();
            return null;
        });
    }

    /** {@code true}, solange neue Aufgaben angenommen werden. */
    public boolean isOpen() {
        return !delegate.isShutdown();
    }

    /** Nimmt keine Aufgaben mehr an und wartet höchstens {@link #CLOSE_TIMEOUT} auf laufende. */
    @Override
    public void close() {
        close(CLOSE_TIMEOUT);
    }

    /** Nimmt keine Aufgaben mehr an und wartet höchstens {@code timeout}; danach werden Threads unterbrochen. */
    public void close(Duration timeout) {
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                int dropped = delegate.shutdownNow().size();
                log.warn("Asynchrone Aufgaben nach {} ms abgebrochen ({} nicht gestartet)", timeout.toMillis(),
                        dropped);
            }
        } catch (InterruptedException e) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
