package net.retromc.core.api.social;

/** Ergebnis einer Freundes-Aktion; die Fehler entsprechen den Keys {@code friend.err.*} (Spec 01 §3.1, §7). */
public enum FriendOutcome {
    /** Anfrage gesendet ({@code friend.request.sent}). */
    SENT,
    /** Jetzt befreundet: angenommen oder Gegenanfrage vorhanden ({@code friend.added}). */
    ADDED,
    /** Anfrage abgelehnt ({@code friend.denied}). */
    DENIED,
    /** Freundschaft beendet ({@code friend.removed}). */
    REMOVED,
    /** {@code friend.err.self}. */
    SELF,
    /** {@code error.player.unknown}. */
    UNKNOWN_PLAYER,
    /** {@code friend.err.already}. */
    ALREADY_FRIENDS,
    /** {@code friend.err.pending}. */
    ALREADY_PENDING,
    /** Keine Anfrage dieses Spielers vorhanden. */
    NO_REQUEST,
    /** Nicht befreundet. */
    NOT_FRIENDS,
    /** Eigene Liste voll ({@code friend.err.limit.self} mit {max}). */
    LIMIT_SELF,
    /** Liste des anderen voll oder > 100 offene Anfragen ({@code friend.err.limit.other}). */
    LIMIT_OTHER,
    /** Ziel nimmt keine Anfragen an oder ignoriert den Absender ({@code friend.err.blocked}). */
    BLOCKED
}
