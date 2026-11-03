package net.retromc.core.api.stats;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.util.LevelCurve;

/**
 * Globales Level des Spielers ({@code stats.network_level}, Spec 02 §8.1, K13): Chat-Level (Spec 01), Kill-XP
 * (Spec 03), Bingo/PartyGames/GunGame-XP (Spec 06). Kurve {@code xpFür(n) = 250·n·(n+1)} ({@link LevelCurve}).
 */
public interface NetworkLevelService {

    /** Level und XP; unbekannte Spieler haben Level 0. */
    CompletableFuture<LevelCurve.Progress> level(UUID player);

    /**
     * Schreibt XP gut ({@code xp ≥ 0}) und liefert den Aufstieg, falls das Level steigt.
     *
     * @param source Herkunft für Logs, z. B. {@code kill} oder {@code bingo}
     */
    CompletableFuture<Optional<LevelUp>> addXp(UUID player, String source, long xp);
}
