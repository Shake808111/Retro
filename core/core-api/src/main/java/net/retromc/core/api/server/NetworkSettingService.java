package net.retromc.core.api.server;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Netzwerkweite Einstellungen mit Hot-Reload, Tabelle {@code core.network_setting (key, value JSONB)}
 * (Spec 01 §2.2; {@code /netreload}, §3.6 #88). Werte sind JSON-Texte; die Auswertung macht der Nutzer.
 */
public interface NetworkSettingService {

    String MAINTENANCE = "maintenance";
    /** MOTD, u. a. {@code motd.news} für Zeile 2 (Spec 01 §4.1). */
    String MOTD = "motd";
    String TABLIST = "tablist";

    CompletableFuture<Optional<String>> get(String key);

    /** Schreibt einen Wert (JSON) und setzt {@code updated_at}. */
    CompletableFuture<Void> put(String key, String json);

    /** Alle Einstellungen (für {@code /netreload}). */
    CompletableFuture<Map<String, String>> all();
}
