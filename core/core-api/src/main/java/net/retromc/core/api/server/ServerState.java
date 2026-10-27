package net.retromc.core.api.server;

import java.util.EnumSet;
import java.util.Set;

/**
 * Zustand eines Servers im Valkey-Hash {@code server:{name}} (Feld {@code state}), Tabelle Spec 08 §6.1.
 * BOOTING, ONLINE, DRAINING und STOPPING setzt paper-core; WAITING bis ENDING setzt der Spiel-Rahmen über
 * {@code ServerLifecycle.setState}.
 */
public enum ServerState {
    /** Prozess läuft, Plugins laden. */
    BOOTING("§7"),
    /** Lobby oder Dauerserver bereit. */
    ONLINE("§a"),
    /** Spielserver, Wartelobby offen. */
    WAITING("§a"),
    /** Countdown läuft. */
    STARTING("§e"),
    /** Runde läuft (nur Zuschauer/Rejoin nach Spiel-Rahmen). */
    INGAME("§c"),
    /** Siegerphase. */
    ENDING("§6"),
    /** Nimmt keine neuen Spieler an; Stopp, sobald leer oder nach 600 s. */
    DRAINING("§6"),
    /** Spieler werden verschoben, Daten geschrieben; danach verschwindet der Schlüssel. */
    STOPPING("§8");

    private final String color;

    ServerState(String color) {
        this.color = color;
    }

    /** Ob neue Spieler zugewiesen werden dürfen: ONLINE, WAITING, STARTING (Spec 08 §6.1). */
    public boolean acceptsPlayers() {
        return this == ONLINE || this == WAITING || this == STARTING;
    }

    /** Zustandsfarbe {@code stateColor} für {@code /cloud list} und GUI A (Spec 08 §5.1). */
    public String color() {
        return color;
    }

    /**
     * Erlaubte Folgezustände laut Spalte „Übergänge“ (Spec 08 §6.1). STOPPING ist zusätzlich aus jedem Zustand
     * erreichbar, weil {@code SHUTDOWN} und {@code onDisable} sofort stoppen (Spec 08 §6.2 Nr. 8, §6.10 Nr. 1).
     */
    public Set<ServerState> successors() {
        return switch (this) {
            case BOOTING -> EnumSet.of(ONLINE, WAITING, STOPPING);
            case ONLINE -> EnumSet.of(DRAINING, STOPPING);
            case WAITING -> EnumSet.of(STARTING, STOPPING);
            case STARTING -> EnumSet.of(WAITING, INGAME, STOPPING);
            case INGAME -> EnumSet.of(ENDING, STOPPING);
            case ENDING, DRAINING -> EnumSet.of(STOPPING);
            case STOPPING -> EnumSet.noneOf(ServerState.class);
        };
    }

    public boolean canTransitionTo(ServerState next) {
        return successors().contains(next);
    }
}
