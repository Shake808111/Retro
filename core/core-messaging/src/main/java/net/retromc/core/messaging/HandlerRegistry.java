package net.retromc.core.messaging;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.retromc.core.api.msg.Envelope;

/** Handler je Kanal und Typ; gemeinsamer Teil von {@link ValkeyMessageBus} und {@link LocalMessageBus}. */
final class HandlerRegistry {

    private static final Logger LOG = System.getLogger(HandlerRegistry.class.getName());

    private final Map<NetworkChannel, List<Handler<?>>> handlers = new ConcurrentHashMap<>();

    /** Fügt einen Handler hinzu; {@code true}, wenn er der erste seines Kanals ist. */
    synchronized boolean add(Handler<?> handler) {
        List<Handler<?>> list = handlers.computeIfAbsent(handler.type().channel(), key -> new CopyOnWriteArrayList<>());
        list.add(handler);
        return list.size() == 1;
    }

    /** Entfernt einen Handler; {@code true}, wenn der Kanal danach keine Handler mehr hat. */
    synchronized boolean remove(Handler<?> handler) {
        List<Handler<?>> list = handlers.get(handler.type().channel());
        if (list == null || !list.remove(handler)) {
            return false;
        }
        if (list.isEmpty()) {
            handlers.remove(handler.type().channel());
            return true;
        }
        return false;
    }

    /** Stellt einen dekodierten Envelope an alle Handler seines Typs zu; Fehler eines Handlers werden geloggt. */
    void dispatch(NetworkChannel channel, Envelope<?> envelope) {
        for (Handler<?> handler : handlers.getOrDefault(channel, List.of())) {
            if (handler.type().name().equals(envelope.t())) {
                try {
                    handler.deliver(envelope);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Handler für " + envelope.t() + " ist fehlgeschlagen", e);
                }
            }
        }
    }

    /** Handler eines Typs. */
    record Handler<P>(MessageType<P> type, Consumer<Envelope<P>> consumer) {

        void deliver(Envelope<?> envelope) {
            consumer.accept(type.typed(envelope));
        }
    }
}
