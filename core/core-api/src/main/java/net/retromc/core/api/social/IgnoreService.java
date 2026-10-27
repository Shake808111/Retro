package net.retromc.core.api.social;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Ignorieren ({@code social.ignore}, Cache {@code ignore:{uuid}}; Spec 01 §3.2, §6.7 Nr. 2). Ignorieren blockiert
 * PN, Party-Einladungen, Freundschaftsanfragen, Clan-Einladungen und Chatzeilen (Filter auf paper-core).
 */
public interface IgnoreService {

    /** Höchstzahl ignorierter Spieler. */
    int MAX_IGNORED = 100;

    /** Ignoriert {@code owner} den Spieler {@code target}? */
    CompletableFuture<Boolean> ignores(UUID owner, UUID target);

    /** Alle von {@code owner} ignorierten Spieler (Chatfilter). */
    CompletableFuture<Set<UUID>> ignored(UUID owner);

    CompletableFuture<IgnoreOutcome> add(UUID owner, UUID target);

    /** @return {@code false}, wenn der Spieler nicht ignoriert wurde */
    CompletableFuture<Boolean> remove(UUID owner, UUID target);

    /** Liste für {@code /ignore list}, alphabetisch. */
    CompletableFuture<List<PlayerRef>> list(UUID owner);
}
