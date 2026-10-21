package net.retromc.core.messaging;

import java.util.function.Consumer;

/**
 * Nachrichtenbus zwischen Proxy, Paper-Servern und Diensten.
 * Nutzlast vorerst als Text (geplant: JSON mit Typfeld); Zustellung "höchstens einmal" (Pub/Sub).
 * Für "mindestens einmal" sind laut Notiz 07 Valkey Streams vorgesehen.
 */
public interface MessageBus extends AutoCloseable {

    void publish(NetworkChannel channel, String payload);

    void subscribe(NetworkChannel channel, Consumer<String> handler);

    @Override
    void close();
}
