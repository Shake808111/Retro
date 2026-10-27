package net.retromc.core.messaging;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;

/**
 * {@link MessageBus} innerhalb eines Prozesses: mehrere Busse an einem {@link Hub} verhalten sich wie getrennte
 * Server an Valkey (gleicher Codec, gleiche 64-KiB-Grenze, asynchrone Zustellung in Sendereihenfolge). Für Tests und
 * den Betrieb ohne Valkey (z. B. Werkzeuge).
 */
public final class LocalMessageBus implements MessageBus {

    private final Hub hub;
    private final String source;
    private final Clock clock;
    private final HandlerRegistry handlers = new HandlerRegistry();
    private volatile boolean closed;

    private LocalMessageBus(Hub hub, String source, Clock clock) {
        this.hub = hub;
        this.source = source;
        this.clock = clock;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public <P> Envelope<P> envelope(MessageType<P> type, P payload) {
        return Envelope.create(type.name(), type.version(), source, type.cast(payload), clock);
    }

    @Override
    public <R> Envelope<R> reply(Envelope<?> request, MessageType<R> type, R payload) {
        return request.reply(type.name(), type.version(), source, type.cast(payload), clock);
    }

    @Override
    public CompletableFuture<Void> publish(Envelope<?> envelope) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bus ist geschlossen"));
        }
        try {
            String json = hub.codec.encode(envelope);
            NetworkChannel channel = MessageType.byName(envelope.t()).orElseThrow().channel();
            return CompletableFuture.runAsync(() -> hub.deliver(channel, json), hub.executor);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public <P> CompletableFuture<Registration> subscribe(MessageType<P> type, Consumer<Envelope<P>> handler) {
        HandlerRegistry.Handler<P> entry = new HandlerRegistry.Handler<>(type, handler);
        handlers.add(entry);
        Registration registration = () -> handlers.remove(entry);
        return CompletableFuture.completedFuture(registration);
    }

    @Override
    public void close() {
        closed = true;
        hub.buses.remove(this);
    }

    /** Gemeinsames „Netz“ mehrerer lokaler Busse. */
    public static final class Hub implements AutoCloseable {

        private final EnvelopeCodec codec = new EnvelopeCodec();
        private final List<LocalMessageBus> buses = new CopyOnWriteArrayList<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("local-bus").factory());
        private final Clock clock;

        public Hub() {
            this(Clock.systemUTC());
        }

        public Hub(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        /** Neuer Bus mit eigener Quelle. */
        public LocalMessageBus connect(String source) {
            if (Objects.requireNonNull(source, "source").isBlank()) {
                throw new IllegalArgumentException("source darf nicht leer sein");
            }
            LocalMessageBus bus = new LocalMessageBus(this, source, clock);
            buses.add(bus);
            return bus;
        }

        private void deliver(NetworkChannel channel, String json) {
            Optional<Envelope<?>> envelope = codec.decode(json);
            envelope.ifPresent(value -> buses.forEach(bus -> bus.handlers.dispatch(channel, value)));
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
