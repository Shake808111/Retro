package dev.netzwerk.game.framework;

import java.util.Objects;

/**
 * Beschreibung eines Spielmodus.
 *
 * @param modeId        technische ID, z. B. {@code bedwars} (Valkey, Statistik-Tabellen, Queues)
 * @param displayName   Anzeigename
 * @param defaultCombat Standard-Kampfprofil; Varianten können später abweichen
 */
public record GameDefinition(String modeId, String displayName, CombatProfile defaultCombat) {

    public GameDefinition {
        Objects.requireNonNull(modeId, "modeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(defaultCombat, "defaultCombat");
        if (!modeId.matches("[a-z][a-z0-9-]{0,23}")) {
            // VARCHAR(24) in stats.match.mode / stats.total.mode (db/migrations/V1__init.sql)
            throw new IllegalArgumentException("modeId muss [a-z][a-z0-9-]{0,23} entsprechen: " + modeId);
        }
    }
}
