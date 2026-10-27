package net.retromc.core.api.social;

/** Ergebnis einer Party-Aktion (Spec 01 §3.3; Fehler entsprechen {@code party.err.*}). */
public enum PartyOutcome {
    /** Einladung verschickt (Party bei Bedarf erstellt). */
    INVITED,
    /** Beigetreten ({@code party.joined}). */
    JOINED,
    /** Beitrittsanfrage gesendet ({@code party.request.sent}). */
    REQUEST_SENT,
    /** Einladung abgelehnt. */
    DENIED,
    /** Verlassen ({@code party.left}); ein neuer Leiter steht in der Party. */
    LEFT,
    /** Mitglied entfernt. */
    KICKED,
    /** Leitung übertragen ({@code party.leader.new}). */
    PROMOTED,
    /** Aufgelöst ({@code party.disbanded}). */
    DISBANDED,
    /** Einstellung geändert (public, publicfilter, requests). */
    UPDATED,
    NOT_IN_PARTY,
    /** {@code party.err.notleader}. */
    NOT_LEADER,
    /** {@code party.err.full}. */
    FULL,
    /** {@code party.err.already.member}. */
    ALREADY_MEMBER,
    /** {@code party.err.already.invited}. */
    ALREADY_INVITED,
    /** {@code party_from} oder Ignorieren verbieten die Einladung ({@code party.err.invite.denied}). */
    INVITE_BLOCKED,
    /** {@code party.err.noinvite}. */
    NO_INVITE,
    /** Schon in einer Party ({@code party.err.inparty}). */
    IN_PARTY,
    /** {@code party.err.notpublic}. */
    NOT_PUBLIC,
    /** {@code party.err.filter}. */
    FILTERED,
    /** {@code party.err.nopublic}. */
    NO_PUBLIC_PARTY,
    /** Öffentliche Party erst ab Premium ({@code error.rank.required}). */
    RANK_REQUIRED,
    /** Ziel ist offline. */
    TARGET_OFFLINE,
    /** Aktion auf sich selbst. */
    SELF,
    /** Ziel ist kein Mitglied. */
    NOT_MEMBER,
    /** Keine Beitrittsanfrage dieses Spielers. */
    NO_REQUEST
}
