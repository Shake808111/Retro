package net.retromc.paper.core.server;

import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import org.jspecify.annotations.Nullable;

/**
 * Eigener Eintrag in der Server-Registry (Spec 08 §8.1 {@code ServerLifecycle}): {@code server:{name}} mit Heartbeat
 * alle 5 s und TTL 15 s, {@code SERVER_STATE} bei Zustandswechsel. BOOTING, ONLINE, DRAINING und STOPPING setzt
 * paper-core, WAITING bis ENDING der Spiel-Rahmen (Spec 08 §6.1). Austauschbar über die Service-Registry.
 */
public interface ServerLifecycle {

    /** Aktueller Eintrag. */
    ServerInfo self();

    /** Wechselt den Zustand (nur erlaubte Übergänge, Spec 08 §6.1), schreibt sofort und sendet {@code SERVER_STATE}. */
    void setState(ServerState state);

    void setMap(@Nullable String map);

    void setMatchId(@Nullable String matchId);

    void setPrivateRound(boolean privateRound);

    /**
     * Modus, Variante, Anzeigename und Höchstzahl des Spielservers (der Rahmen kennt sie genauer als das Template,
     * z. B. {@code bedwars_classic}).
     */
    void describe(String mode, String variant, String displayName, int max);

    /**
     * Zusatzfeld des Spiel-Rahmens im Hash ({@code countdown}, {@code spectatable}, {@code teams}, {@code teamSize},
     * {@code privateId}, {@code owner}, Spec 03 §2.2); {@code null} entfernt es. Wird sofort geschrieben.
     */
    void setField(String field, @Nullable String value);

    /** Sendet {@code SERVER_STATE} mit dem aktuellen Stand (z. B. nach Spielerzahlwechsel, höchstens 1/s). */
    void publish();

    /** Zählt gespielte Runden ({@code infra.server_instance.matches}). */
    void incrementMatches();

    /** Beendet den Server geordnet (Spec 08 §6.10 Nr. 1): STOPPING, Spieler in die Lobby, nach 2 s Stopp. */
    void requestShutdown(String reason);
}
