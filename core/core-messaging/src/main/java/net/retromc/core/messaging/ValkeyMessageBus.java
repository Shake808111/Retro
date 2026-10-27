package net.retromc.core.messaging;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.ValkeyConnector;

/** {@link MessageBus} über Valkey Pub/Sub (Lettuce) mit dem JSON-Envelope aus Spec 08 §6.12. */
public final class ValkeyMessageBus implements MessageBus {

    private static final Logger LOG = System.getLogger(ValkeyMessageBus.class.getName());

    private final RedisClient client;
    private final boolean ownsClient;
    private final String source;
    private final Clock clock;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final StatefulRedisConnection<String, String> publisher;
    private final StatefulRedisPubSubConnection<String, String> subscriber;
    private final HandlerRegistry handlers = new HandlerRegistry();
    private final Map<NetworkChannel, CompletableFuture<Void>> active = new ConcurrentHashMap<>();

    /** Eigener Client zur URL, z. B. {@code redis://valkey:6379}; wird mit dem Bus geschlossen. */
    public ValkeyMessageBus(String valkeyUrl, String source) {
        this(RedisClient.create(valkeyUrl), true, source, Clock.systemUTC());
    }

    /** Nutzt den Client des Connectors (wird beim Schließen des Busses nicht beendet). */
    public ValkeyMessageBus(ValkeyConnector connector, String source) {
        this(connector.client(), false, source, Clock.systemUTC());
    }

    /** Geteilter Client mit eigener Uhr (Tests). */
    public ValkeyMessageBus(RedisClient client, String source, Clock clock) {
        this(client, false, source, clock);
    }

    private ValkeyMessageBus(RedisClient client, boolean ownsClient, String source, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.ownsClient = ownsClient;
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source darf nicht leer sein");
        }
        this.publisher = client.connect();
        this.subscriber = client.connectPubSub();
        this.subscriber.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                receive(channel, message);
            }
        });
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
        String json;
        NetworkChannel channel;
        try {
            channel = MessageType.byName(envelope.t()).orElseThrow(
                    () -> new IllegalArgumentException("Unbekannter Nachrichtentyp: " + envelope.t())).channel();
            json = codec.encode(envelope);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return publisher.async().publish(channel.key(), json).toCompletableFuture().thenApply(receivers -> null);
    }

    @Override
    public <P> CompletableFuture<Registration> subscribe(MessageType<P> type, Consumer<Envelope<P>> handler) {
        HandlerRegistry.Handler<P> entry = new HandlerRegistry.Handler<>(type, handler);
        NetworkChannel channel = type.channel();
        CompletableFuture<Void> ready;
        synchronized (active) {
            handlers.add(entry);
            ready = active.computeIfAbsent(channel,
                    key -> subscriber.async().subscribe(key.key()).toCompletableFuture().thenApply(ignored -> null));
        }
        CompletableFuture<Void> subscribed = ready;
        subscribed.whenComplete((ignored, error) -> {
            if (error != null) {
                // Fehlgeschlagenes SUBSCRIBE nicht zwischenspeichern, damit ein neuer Versuch möglich ist.
                active.remove(channel, subscribed);
                handlers.remove(entry);
            }
        });
        Registration registration = () -> unsubscribe(entry);
        return ready.thenApply(ignored -> registration);
    }

    private void unsubscribe(HandlerRegistry.Handler<?> entry) {
        synchronized (active) {
            if (handlers.remove(entry) && active.remove(entry.type().channel()) != null && subscriber.isOpen()) {
                subscriber.async().unsubscribe(entry.type().channel().key());
            }
        }
    }

    private void receive(String channelKey, String message) {
        Optional<NetworkChannel> channel = NetworkChannel.byKey(channelKey);
        if (channel.isEmpty()) {
            return;
        }
        try {
            codec.decode(message).ifPresent(envelope -> handlers.dispatch(channel.get(), envelope));
        } catch (MessageFormatException e) {
            LOG.log(Level.WARNING, "Ungültige Nachricht auf " + channelKey + " verworfen: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        subscriber.close();
        publisher.close();
        if (ownsClient) {
            client.shutdown();
        }
    }
}
