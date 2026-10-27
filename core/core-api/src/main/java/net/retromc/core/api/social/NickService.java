package net.retromc.core.api.social;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.server.ServerCategory;

/**
 * Nicks (Spec 01 §3.5 #69–72, §6.11, §8.1). Zuweisung nur auf GAME-Servern; zwei Spieler erhalten nie denselben
 * Nick ({@code nick:name:{lower}} als Kollisionsschutz, A23). Sendet {@code NICK_CHANGED}.
 */
public interface NickService {

    /** Monitoring-Warnung, wenn weniger freie Pool-Namen übrig sind (Spec 01 §6.11 Nr. 7). */
    int POOL_WARN_FREE = 50;

    CompletableFuture<Optional<Nick>> nickOf(UUID player);

    /**
     * Nick aus dem Pool zuweisen (enabled, frei, nicht als echter Spieler online); leer, wenn die Kategorie nicht
     * GAME ist oder kein Name frei ist. Ein bestehender Nick ({@code nick_keep}) wird beibehalten.
     */
    CompletableFuture<Optional<Nick>> assign(UUID player, ServerCategory category);

    /** Nick freigeben (Rundenende ohne {@code nick_keep}, {@code /unnick}, Logout). */
    CompletableFuture<Void> release(UUID player);

    /** Echte UUID zu einem aktiven Nick (nur für Freunde/Team, Spec 01 §6.7 Nr. 4). */
    CompletableFuture<Optional<UUID>> resolve(String nickName);

    /** Aktive Nicks der angegebenen Spieler ({@code /nicks} für das Team). */
    CompletableFuture<Map<UUID, Nick>> nicks(Collection<UUID> players);

    /** Freie Namen im Pool. */
    CompletableFuture<Integer> freeCount();
}
