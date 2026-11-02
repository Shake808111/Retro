package net.retromc.platform.common.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Asynchrone Arbeit und Rückkehr auf den Haupt-Thread. */
class ExecutorsTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorsTest.class);

    /** Simulierter Haupt-Thread: Aufgaben warten in einer Schlange auf den nächsten "Tick". */
    private static final class FakeServer {

        private final Deque<Runnable> queue = new ArrayDeque<>();
        private boolean onMain;

        MainThreadExecutor executor() {
            return new MainThreadExecutor(() -> onMain, queue::add, LOG);
        }

        void tick() {
            onMain = true;
            try {
                while (!queue.isEmpty()) {
                    queue.poll().run();
                }
            } finally {
                onMain = false;
            }
        }
    }

    @Test
    void asyncLaeuftAufVirtuellenThreadsMitPraefix() throws Exception {
        try (AsyncExecutor async = new AsyncExecutor("test-async-", LOG)) {
            Thread thread = async.supply(Thread::currentThread).get(5, TimeUnit.SECONDS);

            assertTrue(thread.isVirtual());
            assertTrue(thread.getName().startsWith("test-async-"), thread.getName());
        }
    }

    @Test
    void fehlerKommenInDerZukunftAn() {
        try (AsyncExecutor async = new AsyncExecutor("test-async-", LOG)) {
            CompletableFuture<Object> future = async.supply(() -> {
                throw new IllegalStateException("kaputt");
            });

            ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
        }
    }

    @Test
    void schliessenWartetAufLaufendeArbeit() {
        AtomicBoolean done = new AtomicBoolean();
        AsyncExecutor async = new AsyncExecutor("test-async-", LOG);
        async.run(() -> {
            try {
                Thread.sleep(100);
                done.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        async.close(Duration.ofSeconds(5));

        assertTrue(done.get());
        assertFalse(async.isOpen());
        assertThrows(RejectedExecutionException.class, () -> async.execute(() -> { }));
    }

    @Test
    void hauptThreadFuehrtSofortAusSonstImNaechstenTick() {
        FakeServer server = new FakeServer();
        MainThreadExecutor main = server.executor();
        List<String> calls = new ArrayList<>();

        main.execute(() -> calls.add("eingeplant"));
        assertTrue(calls.isEmpty());
        server.tick();
        assertEquals(List.of("eingeplant"), calls);

        server.onMain = true;
        main.execute(() -> calls.add("sofort"));
        assertEquals(List.of("eingeplant", "sofort"), calls);
    }

    @Test
    void ergebnisWirdAufDemHauptThreadVerarbeitet() {
        FakeServer server = new FakeServer();
        MainThreadExecutor main = server.executor();
        CompletableFuture<String> loaded = new CompletableFuture<>();
        List<String> seen = new ArrayList<>();

        main.accept(loaded, "Profil laden", value -> seen.add(value + "@" + server.onMain));
        loaded.complete("profil");
        assertTrue(seen.isEmpty());
        server.tick();

        assertEquals(List.of("profil@true"), seen);
    }

    @Test
    void resumeSchliesstAufDemHauptThreadAb() {
        FakeServer server = new FakeServer();
        CompletableFuture<Integer> source = new CompletableFuture<>();
        CompletableFuture<Integer> resumed = server.executor().resume(source);

        source.complete(7);
        assertFalse(resumed.isDone());
        server.tick();

        assertEquals(7, resumed.join());
    }

    @Test
    void fehlerBeimZurueckkehrenWerdenNurProtokolliert() {
        FakeServer server = new FakeServer();
        CompletableFuture<String> failed = CompletableFuture.failedFuture(new IllegalStateException("DB weg"));
        List<String> seen = new ArrayList<>();

        server.executor().accept(failed, "Profil laden", seen::add);
        server.tick();

        assertTrue(seen.isEmpty());
    }
}
