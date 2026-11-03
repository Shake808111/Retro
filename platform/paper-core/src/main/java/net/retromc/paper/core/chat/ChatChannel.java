package net.retromc.paper.core.chat;

/** Chat-Kanal einer Nachricht (Spec 02 §6.1 Schritt 1); bestimmt das Kanalpräfix und die Empfänger. */
public enum ChatChannel {
    /** Normaler Chat des Servers (ohne Präfix). */
    GLOBAL,
    /** Teamchat in Runden (ohne Präfix, nur das Team). */
    TEAM,
    /** {@code @a}/{@code @all} im Teamspiel: an alle, Präfix {@code [@all]}. */
    ALL,
    /** Zuschauer: Präfix {@code [OFF]}, nur Zuschauer. */
    SPECTATOR,
    /** Party-Chat (Proxy). */
    PARTY,
    /** Clan-Chat (Proxy). */
    CLAN
}
