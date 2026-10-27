package net.retromc.core.messaging;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;

/**
 * Anfrage/Antwort über den Bus (Spec 08 §1.1, §6.12 Nr. 4): Korrelation über {@code id}/{@code re}, Timeout
 * {@link #DEFAULT_TIMEOUT} (3 s), keine automatische Wiederholung (Admin-Aktionen sind nicht idempotent).
 * Antworten auf fremde Anfragen werden ignoriert; bei mehreren Antworten gilt die erste.
 *
 * @param <Q> Nutzlast der Anfrage (z. B. {@code CtlRequest})
 * @param <R> Nutzlast der Antwort (z. B. {@code CtlReply})
 */
public final class RequestReply<Q, R> implements AutoCloseable {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger LOG = System.getLogger(RequestReply.class.getName());

    private final MessageBus bus;
    private final MessageType<Q> requestType;
    private final Map<UUID, CompletableFuture<Envelope<R>>> pending = new ConcurrentHashMap<>();
    private volatile Registration replies = () -> { };

    private RequestReply(MessageBus bus, MessageType<Q> requestType) {
        this.bus = bus;
        this.requestType = requestType;
    }

    /** Öffnet einen Anfragekanal; erfüllt, sobald Antworten empfangen werden. */
    public static <Q, R> CompletableFuture<RequestReply<Q, R>> open(MessageBus bus, MessageType<Q> requestType,
            MessageType<R> replyType) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(requestType, "requestType");
        Objects.requireNonNull(replyType, "replyType");
        RequestReply<Q, R> client = new RequestReply<>(bus, requestType);
        return bus.subscribe(replyType, client::onReply).thenApply(registration -> {
            client.replies = registration;
            return client;
        });
    }

    /** Anfrage mit {@link #DEFAULT_TIMEOUT}. */
    public CompletableFuture<Envelope<R>> request(Q payload) {
        return request(payload, DEFAULT_TIMEOUT);
    }

    /** Anfrage; schlägt nach {@code timeout} mit {@link java.util.concurrent.TimeoutException} fehl. */
    public CompletableFuture<Envelope<R>> request(Q payload, Duration timeout) {
        Envelope<Q> envelope = bus.envelope(requestType, payload);
        CompletableFuture<Envelope<R>> answer = new CompletableFuture<>();
        pending.put(envelope.id(), answer);
        answer.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((reply, error) -> pending.remove(envelope.id()));
        bus.publish(envelope).whenComplete((ignored, error) -> {
            if (error != null) {
                answer.completeExceptionally(error);
            }
        });
        return answer;
    }

    /** Offene Anfragen (für Tests und Monitoring). */
    public int pendingCount() {
        return pending.size();
    }

    private void onReply(Envelope<R> reply) {
        UUID re = reply.re();
        if (re == null) {
            return;
        }
        CompletableFuture<Envelope<R>> answer = pending.remove(re);
        if (answer != null) {
            answer.complete(reply);
        }
    }

    /**
     * Beantwortet Anfragen eines Typs: {@code handler} liefert die Antwort-Nutzlast, die mit {@code re} = Anfrage-ID
     * gesendet wird. Fehler des Handlers werden geloggt (keine Antwort → Timeout beim Anfragenden).
     */
    public static <Q, R> CompletableFuture<Registration> serve(MessageBus bus, MessageType<Q> requestType,
            MessageType<R> replyType, Function<Envelope<Q>, CompletableFuture<R>> handler) {
        return bus.subscribe(requestType, request -> handler.apply(request)
                .thenCompose(answer -> bus.publish(bus.reply(request, replyType, answer)))
                .exceptionally(error -> {
                    LOG.log(Level.WARNING, "Anfrage " + request.t() + " " + request.id() + " ohne Antwort", error);
                    return null;
                }));
    }

    /** Beendet das Antwort-Abo; offene Anfragen werden abgebrochen. */
    @Override
    public void close() {
        replies.close();
        pending.values().forEach(answer -> answer.completeExceptionally(
                new CancellationException("Anfragekanal geschlossen")));
        pending.clear();
    }
}
