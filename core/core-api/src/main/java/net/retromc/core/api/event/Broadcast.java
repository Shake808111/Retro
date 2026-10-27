package net.retromc.core.api.event;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Netzwerkweite Meldung ({@code BROADCAST}, net:chat; Spec 01 §2.4), z. B. {@code joinme.broadcast} an alle Lobbys
 * (§6.17) oder {@code maintenance.countdown} (§6.15). Gerendert wird je Empfänger in seiner Sprache.
 *
 * @param key i18n-Schlüssel
 * @param args Platzhalter des Schlüssels
 * @param clickCommand Befehl der Klickzeile ({@code [BETRETEN]}) oder {@code null}
 * @param lobbiesOnly nur Spieler auf Lobby-Servern (ohne Silent-Lobby)
 */
public record Broadcast(String key, Map<String, String> args, @Nullable String clickCommand, boolean lobbiesOnly) {

    public Broadcast {
        Objects.requireNonNull(key, "key");
        args = Map.copyOf(args);
    }
}
