package net.retromc.core.api.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Datenzugriff der Statistik (Spec 02 §2, §6.4–§6.10): Modus-Katalog, Rundenverbuchung, Zeitfenster, Bestenlisten
 * in Valkey, {@code /togglestats}, Ratenlimit, Reset-Dialog und Reset sowie die Jobs (Rollover, Reconcile,
 * Partitionen). Standard ist {@code StatsRepository} (core-data); ein Adapter ersetzt ihn über die Service-Registry.
 */
public interface StatsStore {

    /** Alle Modi des Katalogs nach {@code id} (zwischengespeichert). */
    CompletableFuture<List<StatsMode>> modes();

    /** Modus zum Schlüssel, falls im Katalog. */
    default CompletableFuture<Optional<StatsMode>> mode(ModeKey key) {
        return modes().thenApply(all -> all.stream().filter(mode -> mode.key().equals(key)).findFirst());
    }

    /** Verbucht eine Runde genau einmal je {@code matchId} (§6.4 Schritt 2–4). */
    CompletableFuture<SubmitResult> submit(MatchResult result);

    /** Zähler eines Spielers im Fenster (Summe der Tagesbuckets bzw. {@code stats.alltime}). */
    CompletableFuture<StatLine> line(UUID player, ModeKey mode, StatWindow window);

    /** Werte je Map ({@code stats.map_alltime}). */
    CompletableFuture<Map<String, StatLine>> mapLines(UUID player, ModeKey mode);

    /** Platz in der Bestenliste ({@code ZREVRANK + 1}); leer, wenn nicht gelistet. */
    CompletableFuture<OptionalLong> position(UUID player, ModeKey mode, StatPeriod period);

    /** Spieler auf Platz {@code position} (ab 1), falls besetzt. */
    CompletableFuture<Optional<UUID>> byRank(ModeKey mode, StatPeriod period, int position);

    /** Die ersten {@code count} Plätze (Cache {@code top:*}, 30 s). */
    CompletableFuture<List<TopEntry>> top(ModeKey mode, StatPeriod period, int count);

    /** {@code /togglestats}-Zustand. */
    CompletableFuture<Boolean> hidden(UUID player);

    /** Setzt den {@code /togglestats}-Zustand und liefert ihn. */
    CompletableFuture<Boolean> setHidden(UUID player, boolean hidden);

    /** Ratenlimit {@code rl:stats:{uuid}} (1 Abfrage pro Sekunde); {@code false} = zu schnell. */
    CompletableFuture<Boolean> tryQuery(UUID player);

    /** Öffnet den Reset-Dialog ({@code SET NX EX 30}); {@code false}, wenn schon einer offen ist. */
    CompletableFuture<Boolean> openReset(UUID player, ModeKey mode);

    /** Nimmt den offenen Reset-Dialog weg ({@code GETDEL}); leer, wenn keiner offen oder abgelaufen ist. */
    CompletableFuture<Optional<ModeKey>> takeReset(UUID player);

    /** Löscht alle Statistiken eines Modus (30 Tage und gesamt) mit Snapshot (§6.9 Schritt 4–5). */
    CompletableFuture<ResetResult> reset(UUID player, ModeKey mode, ResetScope scope, @Nullable UUID actor);

    /** Baut beide Bestenlisten des Modus neu auf (§6.5 Schritt 3); liefert die Einträge gesamt. */
    CompletableFuture<Long> rebuild(ModeKey mode);

    /** Täglicher Rollover (§6.5 Schritt 2); {@code false}, wenn ein anderer Server ihn schon ausgeführt hat. */
    CompletableFuture<Boolean> rollover(LocalDate today);

    /** Nächtlicher Neuaufbau aller Bestenlisten (§6.5 Schritt 3); {@code false} bei fremder Sperre. */
    CompletableFuture<Boolean> reconcile(LocalDate today);

    /** Partitionen anlegen/löschen (§2.1) und alte Rundenzeilen entfernen (O23, 180 Tage). */
    CompletableFuture<Void> housekeeping(LocalDate today);
}
