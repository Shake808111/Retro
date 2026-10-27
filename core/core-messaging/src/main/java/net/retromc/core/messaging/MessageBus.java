package net.retromc.core.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;

/**
 * Nachrichtenbus zwischen Proxy, Paper-Servern und Diensten (Spec 08 §6.12): typisierte Nachrichten im Envelope,
 * Zustellung „höchstens einmal“ (Pub/Sub, W7). Handler laufen asynchron auf dem Empfangs-Thread des Busses;
 * Bukkit-API nur über den Scheduler (Spec 00 §4.1).
 */
public interface MessageBus extends AutoCloseable {

    /** Absenderkennung im Feld {@code src} (Servername, Proxy-ID oder {@code controller}). */
    String source();

    /** Neuer Envelope dieses Busses (zufällige ID, aktuelle Zeit, eigene Quelle). */
    <P> Envelope<P> envelope(MessageType<P> type, P payload);

    /** Antwort auf {@code request} ({@code re} = ID der Anfrage). */
    <R> Envelope<R> reply(Envelope<?> request, MessageType<R> type, R payload);

    /**
     * Sendet auf dem Kanal des Typs. Schlägt mit {@link MessageTooLargeException} fehl, wenn die Nachricht größer als
     * 64 KiB ist.
     */
    CompletableFuture<Void> publish(Envelope<?> envelope);

    /** Erstellt und sendet eine Nachricht; liefert den gesendeten Envelope. */
    default <P> CompletableFuture<Envelope<P>> publish(MessageType<P> type, P payload) {
        Envelope<P> envelope = envelope(type, payload);
        return publish(envelope).thenApply(ignored -> envelope);
    }

    /**
     * Abonniert einen Typ. Die Zukunft ist erfüllt, sobald das Abo aktiv ist; {@link Registration#close()} beendet es.
     */
    <P> CompletableFuture<Registration> subscribe(MessageType<P> type, Consumer<Envelope<P>> handler);

    @Override
    void close();
}
