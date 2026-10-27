package net.retromc.core.api.social;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wer ist wo online ({@code presence:{uuid}}, Spec 01 §2.3, §6.3, §8.1). Velocity schreibt bei Login und
 * Serverwechsel, erneuert alle 20 s und löscht beim Disconnect; Lobby und Spiel-Rahmen lesen.
 */
public interface PresenceService {

    CompletableFuture<Optional<Presence>> presence(UUID player);

    /** Präsenzen mehrerer Spieler; Offline-Spieler fehlen im Ergebnis. */
    CompletableFuture<Map<UUID, Presence>> presences(Collection<UUID> players);

    CompletableFuture<Optional<String>> serverOf(UUID player);

    /** Schreibt die Präsenz vollständig (Login, Serverwechsel) mit TTL 60 s. */
    CompletableFuture<Void> update(Presence presence);

    /** Heartbeat: TTL der Präsenzen erneuern. */
    CompletableFuture<Void> refresh(Collection<UUID> players);

    /** AFK setzen (paper-core, Spec 01 §6.12) und {@code AFK_CHANGED} senden. */
    CompletableFuture<Void> setAfk(UUID player, boolean afk);

    /** Präsenz löschen (Disconnect, Spec 01 §6.3 Schritt 7). */
    CompletableFuture<Void> remove(UUID player);
}
