package net.retromc.core.api.server;

import java.util.Objects;

/**
 * Eintrag der Server-Registry. Entspricht dem Valkey-Hash {@code server:<name>} mit den Feldern
 * {mode, variant, map, state, online, max} aus Notiz 07, Abschnitt 4 (Valkey key conventions).
 */
public record ServerInfo(
        String name,
        String mode,
        String variant,
        String map,
        ServerState state,
        int online,
        int max) {

    public ServerInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(state, "state");
        if (online < 0 || max < 0) {
            throw new IllegalArgumentException("online und max dürfen nicht negativ sein");
        }
    }

    /** Freie Plätze (nie negativ). */
    public int freeSlots() {
        return Math.max(0, max - online);
    }
}
