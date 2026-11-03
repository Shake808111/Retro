package net.retromc.paper.core.stats;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.SubmitResult;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.stats.StatsReport;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Statistik auf Paper (Spec 02 §8.1 {@code StatsService}) für Spiel-Rahmen, Lobby und Profil: Rundenende verbuchen
 * ({@code StatsSubmittedEvent}), abfragen, Bestenlisten, {@code /stats}-Ausgabe, Rundenblock und Statistik-Profil.
 */
public interface StatsService {

    /** Verbucht die Runde einmal je {@code matchId} (§6.4); danach {@code StatsSubmittedEvent}. */
    CompletableFuture<SubmitResult> submit(MatchResult result);

    /** Werte, Platz und Bericht eines Spielers. */
    CompletableFuture<Snapshot> query(UUID player, ModeKey mode, StatWindow window);

    CompletableFuture<Optional<UUID>> byRank(ModeKey mode, StatPeriod period, int position);

    CompletableFuture<List<TopEntry>> top(ModeKey mode, StatPeriod period, int count);

    /**
     * Sendet den {@code /stats}-Block mit allen Prüfungen (Ratenlimit, Name/#Platz, versteckte Stats).
     *
     * @param target leer = eigene Stats, {@code #N} oder echter Spielername
     */
    void sendStats(CommandSender viewer, ModeKey mode, StatWindow window, String target);

    /** Rundenblock an einen Spieler (Spec 02 §4.7). */
    void roundSummary(Player player, RoundSummary summary);

    /** Modus dieses Servers (aus dem Template); ein Modul kann ihn setzen. */
    ServerMode serverMode();

    void serverMode(ServerMode mode);

    /**
     * Öffnet das Statistik-Profil (Spec 02 §5.4).
     *
     * @param back Callback des Knopfs „Zurück“ (Slot 36) oder {@code null} ohne Knopf
     */
    void openProfile(Player viewer, UUID target, @Nullable Runnable back);

    /**
     * Momentaufnahme einer Abfrage.
     *
     * @param position Platz im Zeitraum; leer bei Fenstern ohne Bestenliste ({@code /statsd}) oder ohne Eintrag
     */
    record Snapshot(StatsMode mode, StatLine line, OptionalLong position, StatsReport report) {
    }
}
