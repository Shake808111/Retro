package net.retromc.core.api.rank;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Ränge aus LuckPerms (Spec 01 §8.1). Genutzt von Kern-UI (Scoreboard „Rang“), Spiel-Rahmen ({@code /forcemap},
 * farbiges GG, volle Server), Lobby (Fortbewegung, Silent-Lobby) und den Social-Limits.
 */
public interface RankService {

    /** Rang inklusive Limits und Ablauf; unbekannte Spieler erhalten {@link Rank#DEFAULT}. */
    CompletableFuture<RankInfo> rank(UUID player);

    /** Ränge mehrerer Spieler (Listen: Freunde, Clan, Party); jeder angefragte Spieler ist im Ergebnis. */
    CompletableFuture<Map<UUID, RankInfo>> ranks(Collection<UUID> players);
}
