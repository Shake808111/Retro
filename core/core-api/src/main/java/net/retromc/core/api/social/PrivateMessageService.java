package net.retromc.core.api.social;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Private Nachrichten (Spec 01 §3.2 #16–18, §6.7). Zustellung über {@code net:chat PRIVATE_MSG} an den Proxy des
 * Empfängers (Multi-Proxy, O30); {@code msg:last} beidseitig, {@code msg:recent} beim Empfänger.
 */
public interface PrivateMessageService {

    /** Einträge in {@code /msg list}. */
    int RECENT_LIMIT = 10;

    /** Gültigkeit von {@code msg:last} für {@code /r}. */
    Duration REPLY_WINDOW = Duration.ofMinutes(30);

    /** Aufbewahrung von {@code msg:recent}. */
    Duration RECENT_RETENTION = Duration.ofHours(24);

    CompletableFuture<MessageResult> send(UUID from, UUID to, String text);

    /** Antwort an {@code msg:last}. */
    CompletableFuture<MessageResult> reply(UUID from, String text);

    /** Letzte 10 empfangene Nachrichten der letzten 24 h, neueste zuerst. */
    CompletableFuture<List<ReceivedMessage>> recent(UUID player);
}
