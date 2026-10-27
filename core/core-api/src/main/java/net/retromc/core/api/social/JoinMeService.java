package net.retromc.core.api.social;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tageszähler für {@code /joinme} ({@code cnt:joinme:{uuid}:{yyyy-mm-dd}}, Reset 00:00 Europe/Berlin, Spec 01 §6.17).
 * Den Broadcast {@code BROADCAST} an alle Lobbys sendet velocity-core.
 */
public interface JoinMeService {

    /** Nutzung verbuchen, wenn das Tageslimit ({@code RankLimits.joinmePerDay}) es erlaubt. */
    CompletableFuture<JoinMeResult> use(UUID player, int perDay);

    /** Heutige Nutzungen. */
    CompletableFuture<Integer> usedToday(UUID player);
}
