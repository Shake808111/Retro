package net.retromc.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.GlobalChat;
import net.retromc.core.api.event.NickChanged;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.payload.CtlLine;
import net.retromc.core.messaging.payload.CtlReply;
import net.retromc.core.messaging.payload.CtlRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LocalBusAndRequestReplyTest {

    private final LocalMessageBus.Hub hub = new LocalMessageBus.Hub();
    private final LocalMessageBus proxy = hub.connect("proxy-1");
    private final LocalMessageBus controller = hub.connect("controller");

    @AfterEach
    void schliessen() {
        proxy.close();
        controller.close();
        hub.close();
    }

    @Test
    void zustellungNurAnPassendeTypen() throws Exception {
        BlockingQueue<Envelope<AfkChanged>> received = new LinkedBlockingQueue<>();
        Registration registration = controller.subscribe(MessageType.AFK_CHANGED, received::add).get();
        proxy.publish(MessageType.NICK_CHANGED, new NickChanged(Samples.A, null, "x"))
                .get();
        Envelope<AfkChanged> sent = proxy.publish(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, true)).get();
        Envelope<AfkChanged> got = received.poll(2, TimeUnit.SECONDS);
        assertEquals(sent, got);
        assertEquals("proxy-1", got.src());
        assertTrue(received.isEmpty());
        registration.close();
        proxy.publish(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, false)).get();
        assertNull(received.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void zuGrosseNachrichtSchlaegtFehl() {
        CompletableFuture<?> result = proxy.publish(MessageType.GLOBAL_CHAT,
                new GlobalChat(Samples.A, "lobby-1", "x".repeat(70_000), 1));
        ExecutionException error = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(MessageTooLargeException.class, error.getCause());
    }

    @Test
    void anfrageWirdBeantwortet() throws Exception {
        RequestReply.serve(controller, MessageType.CTL_REQUEST, MessageType.CTL_REPLY,
                request -> CompletableFuture.completedFuture(new CtlReply(true,
                        List.of(new CtlLine("cloud.list.header", Map.of("count", "1")))))).get();
        try (RequestReply<CtlRequest, CtlReply> client =
                     RequestReply.open(proxy, MessageType.CTL_REQUEST, MessageType.CTL_REPLY).get()) {
            Envelope<CtlReply> reply = client.request(new CtlRequest(Samples.A, "list", List.of())).get(2,
                    TimeUnit.SECONDS);
            assertTrue(reply.d().ok());
            assertEquals("controller", reply.src());
            assertEquals(0, client.pendingCount());
        }
    }

    @Test
    void ohneAntwortTimeoutOhneWiederholung() throws Exception {
        BlockingQueue<Envelope<CtlRequest>> seen = new LinkedBlockingQueue<>();
        controller.subscribe(MessageType.CTL_REQUEST, seen::add).get();
        try (RequestReply<CtlRequest, CtlReply> client =
                     RequestReply.open(proxy, MessageType.CTL_REQUEST, MessageType.CTL_REPLY).get()) {
            CompletableFuture<Envelope<CtlReply>> answer =
                    client.request(new CtlRequest(null, "list", List.of()), Duration.ofMillis(300));
            ExecutionException error = assertThrows(ExecutionException.class, () -> answer.get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, error.getCause());
            assertEquals(0, client.pendingCount());
            Thread.sleep(300);
            assertEquals(1, seen.size(), "keine automatische Wiederholung");
        }
        assertEquals(Duration.ofSeconds(3), RequestReply.DEFAULT_TIMEOUT);
    }

    @Test
    void fremdeAntwortenWerdenIgnoriertUndSchliessenBrichtAb() throws Exception {
        RequestReply<CtlRequest, CtlReply> client =
                RequestReply.open(proxy, MessageType.CTL_REQUEST, MessageType.CTL_REPLY).get();
        CompletableFuture<Envelope<CtlReply>> answer = client.request(new CtlRequest(null, "info", List.of("x")));
        Envelope<CtlRequest> foreign = controller.envelope(MessageType.CTL_REQUEST,
                new CtlRequest(null, "info", List.of()));
        controller.publish(controller.reply(foreign, MessageType.CTL_REPLY, new CtlReply(false, List.of()))).get();
        Thread.sleep(200);
        assertFalse(answer.isDone(), "Antwort auf fremde Anfrage zählt nicht");
        client.close();
        assertThrows(CancellationException.class, answer::get);
    }
}
