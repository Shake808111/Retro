package net.retromc.platform.common.config;

import java.io.Serial;
import java.util.List;

/** Ungültige oder fehlende Umgebungsvariablen; die Meldung nennt alle Probleme auf einmal. */
public final class ConfigException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public ConfigException(List<String> problems) {
        super("Konfiguration ungültig: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Einzelne Probleme, z. B. {@code NETWORK_DB_URL fehlt}. */
    public List<String> problems() {
        return problems;
    }
}
