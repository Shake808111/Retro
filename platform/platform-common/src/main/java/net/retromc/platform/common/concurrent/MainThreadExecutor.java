package net.retromc.platform.common.concurrent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Rückkehr auf den Haupt-Thread (Spec 00 §4.1: Bukkit-API nur dort). Läuft der Aufrufer schon auf dem Haupt-Thread,
 * wird sofort ausgeführt, sonst über den Scheduler der Plattform eingeplant (Paper:
 * {@code Bukkit.getScheduler().runTask}).
 */
public final class MainThreadExecutor implements Executor {

    private final BooleanSupplier onMainThread;
    private final Executor scheduler;
    private final Logger log;

    /**
     * @param onMainThread {@code true}, wenn der aktuelle Thread der Haupt-Thread ist
     * @param scheduler plant eine Aufgabe für den nächsten Tick ein
     */
    public MainThreadExecutor(BooleanSupplier onMainThread, Executor scheduler, Logger log) {
        this.onMainThread = Objects.requireNonNull(onMainThread, "onMainThread");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (onMainThread.getAsBoolean()) {
            task.run();
        } else {
            scheduler.execute(task);
        }
    }

    /** Zukunft, die auf dem Haupt-Thread mit demselben Ergebnis abschließt. */
    public <T> CompletableFuture<T> resume(CompletableFuture<T> future) {
        return future.thenApplyAsync(Function.identity(), this);
    }

    /**
     * Führt {@code action} mit dem Ergebnis auf dem Haupt-Thread aus. Fehler der Zukunft oder der Aktion werden
     * protokolliert ({@code what} beschreibt den Vorgang, z. B. {@code Profil laden}).
     */
    public <T> void accept(CompletableFuture<T> future, String what, Consumer<? super T> action) {
        future.thenAcceptAsync(action, this).exceptionally(error -> {
            log.error("{} fehlgeschlagen", what, unwrap(error));
            return null;
        });
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
