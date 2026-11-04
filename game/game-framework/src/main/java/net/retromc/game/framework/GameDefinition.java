package net.retromc.game.framework;

import java.util.Objects;

/**
 * Beschreibung eines Spielmodus (Spec 03 §8.1).
 *
 * @param modeId           technische ID, z. B. {@code bedwars} (Valkey, Statistik-Tabellen, Queues)
 * @param displayName      Anzeigename
 * @param defaultCombat    Standard-Kampfprofil; Maps ({@code combat}) und Varianten können abweichen
 * @param teamMode         Teamform
 * @param spectatable      Join in laufende Runden als Zuschauer erlaubt ({@code server:{name}.spectatable})
 * @param persistent       Server bleibt nach der Runde bestehen (sonst eine Runde pro Server, Spec 08 §6.10)
 * @param voting           Map-Abstimmung statt fester Map (Spec 03 §5.11, Standard Ära 26: aus)
 * @param showKillerHealth Opfer sieht die Leben des Killers ({@code common.killer_health}, SW/SG)
 */
public record GameDefinition(String modeId, String displayName, CombatProfile defaultCombat, TeamMode teamMode,
        boolean spectatable, boolean persistent, boolean voting, boolean showKillerHealth) {

    public GameDefinition {
        Objects.requireNonNull(modeId, "modeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(defaultCombat, "defaultCombat");
        Objects.requireNonNull(teamMode, "teamMode");
        if (!modeId.matches("[a-z][a-z0-9_]{0,23}")) {
            // VARCHAR(24) in stats.match.mode / stats.total.mode (db/migrations/V1__init.sql)
            throw new IllegalArgumentException("modeId muss [a-z][a-z0-9_]{0,23} entsprechen: " + modeId);
        }
    }

    /** Kurzform: Teammodus, zuschaubar, eine Runde pro Server, ohne Voting und ohne Killer-Leben. */
    public GameDefinition(String modeId, String displayName, CombatProfile defaultCombat) {
        this(modeId, displayName, defaultCombat, TeamMode.TEAMS, true, false, false, false);
    }
}
