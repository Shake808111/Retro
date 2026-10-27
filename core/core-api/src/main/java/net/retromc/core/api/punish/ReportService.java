package net.retromc.core.api.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Meldungen und Chatlogs (Spec 01 §3.6 #73–76, #83–84, §6.9, §8.1). Sendet {@code REPORT_NEW} und
 * {@code REPORT_CLOSED}. Genutzt von Velocity, Spiel-Rahmen (Replay-Kontext) und Web.
 */
public interface ReportService {

    /** Sperre für dasselbe Ziel ({@code cd:report:{reporter}:{target}}). */
    Duration TARGET_COOLDOWN = Duration.ofSeconds(300);

    /** Meldungen je Stunde ({@code cd:report:{reporter}}). */
    int MAX_PER_HOUR = 10;

    /** Zeitraum des Chatpuffers für {@code /chatlog}. */
    Duration CHAT_BUFFER_WINDOW = Duration.ofMinutes(15);

    /** Zeilen im Chatpuffer {@code chatbuf:{server}}. */
    int CHAT_BUFFER_LINES = 200;

    /** Prüfungen aus §6.9 Nr. 2, bei {@link ReportReason#CHAT} Chatlog-Snapshot. */
    CompletableFuture<ReportResult> create(ReportRequest request);

    /** Offene Meldungen ({@code /reports}), älteste zuerst. */
    CompletableFuture<List<Report>> open();

    /** [ÜBERNEHMEN]: Teammitglied übernimmt. */
    CompletableFuture<Optional<Report>> claim(String publicId, UUID staff);

    /** {@code /report close <ID> <ok|abgelehnt>}. */
    CompletableFuture<Optional<Report>> close(String publicId, UUID staff, boolean justified);

    /** Ergebnisse, die seit {@code since} (letzter Logout) abgeschlossen wurden – Zustellung beim Login (§6.9 Nr. 5). */
    CompletableFuture<List<Report>> resultsSince(UUID reporter, Instant since);

    /** Zeile in den Chatpuffer schreiben (paper-core). */
    CompletableFuture<Void> bufferChat(String server, ChatLine line);

    /** {@code /chatlog <Spieler>}: Zeilen des Ziels mit Kontext der letzten 15 min; leer ohne Nachrichten. */
    CompletableFuture<Optional<Chatlog>> createChatlog(@Nullable UUID creator, UUID target, String server);

    CompletableFuture<Optional<Chatlog>> chatlog(UUID id);
}
