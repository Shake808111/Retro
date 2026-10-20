package dev.netzwerk.core.messaging;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** {@link MessageBus} über Valkey Pub/Sub (Lettuce). */
public final class ValkeyMessageBus implements MessageBus {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> publisher;
    private final StatefulRedisPubSubConnection<String, String> subscriber;
    private final Map<String, List<Consumer<String>>> handlers = new ConcurrentHashMap<>();

    /** @param valkeyUrl z. B. {@code redis://valkey:6379} */
    public ValkeyMessageBus(String valkeyUrl) {
        this.client = RedisClient.create(valkeyUrl);
        this.publisher = client.connect();
        this.subscriber = client.connectPubSub();
        this.subscriber.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                handlers.getOrDefault(channel, List.of()).forEach(handler -> handler.accept(message));
            }
        });
    }

    @Override
    public void publish(NetworkChannel channel, String payload) {
        publisher.async().publish(channel.key(), payload);
    }

    @Override
    public void subscribe(NetworkChannel channel, Consumer<String> handler) {
        handlers.computeIfAbsent(channel.key(), key -> new CopyOnWriteArrayList<>()).add(handler);
        subscriber.sync().subscribe(channel.key());
    }

    @Override
    public void close() {
        subscriber.close();
        publisher.close();
        client.shutdown();
    }
}
