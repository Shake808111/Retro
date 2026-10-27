package net.retromc.core.api.social;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Freunde (Spec 01 §3.1, §5.2, §5.3, §6.4, §8.1). Paare werden mit {@link FriendPair#of} normalisiert.
 * Limits kommen aus dem Rang ({@code RankLimits.friends}); Ereignisse {@code FRIEND_REQUEST/ADDED/REMOVED}
 * sendet die Implementierung.
 */
public interface FriendService {

    /** Höchstzahl offener eingehender Anfragen (Spec 01 §6.4 Nr. 3); darüber {@code friend.err.limit.other}. */
    int MAX_INCOMING_REQUESTS = 100;

    /** Einträge je Seite in {@code /friend list}. */
    int LIST_PAGE_SIZE = 10;

    /** Gültigkeit der Bestätigung von {@code /friend clear}. */
    Duration CLEAR_CONFIRM_WINDOW = Duration.ofSeconds(60);

    /** Online/gesamt für das Scoreboard (versteckte Freunde zählen nicht als online). */
    CompletableFuture<OnlineCount> counts(UUID player);

    /** Alle Freunde mit Status; Sortierung über {@link FriendEntry#GUI_ORDER} bzw. {@link FriendEntry#LIST_ORDER}. */
    CompletableFuture<List<FriendEntry>> list(UUID player);

    CompletableFuture<Boolean> areFriends(UUID a, UUID b);

    /** Anfrage nach den Prüfungen aus Spec 01 §6.4 Nr. 1; bei Gegenanfrage sofort befreundet (Nr. 2). */
    CompletableFuture<FriendResult> request(UUID sender, UUID target);

    CompletableFuture<FriendResult> accept(UUID receiver, UUID sender);

    CompletableFuture<FriendResult> deny(UUID receiver, UUID sender);

    /** Alle annehmen bis zum Limit; liefert die Zahl angenommener Anfragen ({@code friend.acceptall}). */
    CompletableFuture<Integer> acceptAll(UUID receiver);

    /** Alle ablehnen; liefert die Zahl ({@code friend.denyall}). */
    CompletableFuture<Integer> denyAll(UUID receiver);

    /** Offene eingehende Anfragen, älteste zuerst. */
    CompletableFuture<List<FriendRequest>> requests(UUID receiver);

    /** Beidseitig entfernen (inklusive Favoriten-Markierungen). */
    CompletableFuture<FriendResult> remove(UUID player, UUID friend);

    /** Liste leeren nach Bestätigung; liefert die Zahl entfernter Freunde. */
    CompletableFuture<Integer> clear(UUID player);

    /** Favorit an/aus ({@code /friend favorite}); liefert den neuen Zustand. */
    CompletableFuture<Boolean> favorite(UUID owner, UUID friend);

    /** Nachspringen prüfen ({@code jump_from}, Favorit mit {@code allow_jump}, Präsenz). */
    CompletableFuture<JumpResult> jump(UUID player, UUID target);

    /**
     * Empfänger von {@code FRIEND_ONLINE/OFFLINE} (Spec 01 §6.3 Schritt 6/7): Freunde mit {@code notify_friends}
     * oder Favorit-{@code notify}; leer, wenn der Spieler {@code show_online=false} hat.
     */
    CompletableFuture<List<UUID>> statusRecipients(UUID player);
}
