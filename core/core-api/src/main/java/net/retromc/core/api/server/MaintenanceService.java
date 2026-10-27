package net.retromc.core.api.server;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Wartungsmodus (Spec 01 §3.6 #87, §6.3 Schritt 1, §6.15). Schalten schreibt {@code core.network_setting} und den
 * Valkey-HASH {@code maintenance} und sendet {@code MAINTENANCE_CHANGED}. Countdown und Kick führt velocity-core aus.
 */
public interface MaintenanceService {

    /** Vorlauf vor dem Kick bei {@code /maintenance on}. */
    Duration COUNTDOWN = Duration.ofSeconds(60);

    /** Sekunden mit Countdown-Ansage (Spec 01 §6.15 Nr. 1, Annahme). */
    List<Integer> ANNOUNCE_SECONDS = List.of(60, 30, 10, 5, 4, 3, 2, 1);

    CompletableFuture<MaintenanceState> state();

    CompletableFuture<MaintenanceState> enable(@Nullable UUID actor, @Nullable String reason);

    CompletableFuture<MaintenanceState> disable(@Nullable UUID actor);

    /** Ob der Spieler auf der Wartungs-Whitelist steht ({@code core.maintenance_whitelist}). */
    CompletableFuture<Boolean> isWhitelisted(UUID player);

    /** @return {@code false}, wenn der Spieler schon eingetragen war */
    CompletableFuture<Boolean> addToWhitelist(UUID player, @Nullable UUID addedBy);

    /** @return {@code false}, wenn der Spieler nicht eingetragen war */
    CompletableFuture<Boolean> removeFromWhitelist(UUID player);

    CompletableFuture<List<UUID>> whitelist();
}
