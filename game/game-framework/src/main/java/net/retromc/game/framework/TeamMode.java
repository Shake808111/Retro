package net.retromc.game.framework;

/** Teamform eines Modus (Spec 03 §8.1 {@code GameDefinition.teamMode}). */
public enum TeamMode {
    /** Jeder für sich (Teamgröße 1, keine Teamwahl). */
    SOLO,
    /** Farbteams Rot, Blau … (Spec 03 §6.6). */
    TEAMS,
    /** Nummerierte Teams {@code Team #n} (SkyWars-Stil, {@code numberedTeams=true}). */
    NUMBERED
}
