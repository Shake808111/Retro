package net.retromc.core.api.social;

/** Ergebnis einer Clan-Aktion (Spec 01 §3.4; Fehler entsprechen {@code clan.err.*}). */
public enum ClanOutcome {
    CREATED,
    INVITED,
    JOINED,
    REQUEST_SENT,
    REQUEST_DENIED,
    LEFT,
    KICKED,
    PROMOTED,
    DEMOTED,
    /** Leitung übertragen (nach Bestätigung). */
    LEADER_TRANSFERRED,
    /** MODERATOR → LEITER verlangt eine Bestätigung ({@code transferLeadership}). */
    CONFIRM_TRANSFER,
    RENAMED,
    DELETED,
    UPDATED,
    STATS_ENABLED,
    STATS_DISABLED,
    /** {@code clan.reset.done}. */
    STATS_RESET,
    /** {@code clan.err.name}. */
    INVALID_NAME,
    /** {@code clan.err.tag}. */
    INVALID_TAG,
    /** {@code clan.err.name.taken}. */
    NAME_TAKEN,
    /** {@code clan.err.tag.taken}. */
    TAG_TAKEN,
    /** Schon in einem Clan ({@code clan.err.inclan}). */
    IN_CLAN,
    NOT_IN_CLAN,
    /** Ziel ist schon in einem Clan. */
    TARGET_IN_CLAN,
    /** {@code clan.err.full}. */
    FULL,
    /** Rolle reicht nicht. */
    NOT_ALLOWED,
    NOT_LEADER,
    /** Leiter muss vorher übertragen oder löschen ({@code clan.err.leader.leave}). */
    LEADER_CANNOT_LEAVE,
    /** {@code clan.err.rename.limit}. */
    RENAME_LIMIT,
    NO_INVITE,
    ALREADY_INVITED,
    NO_REQUEST,
    ALREADY_REQUESTED,
    /** Beitritt nur per Einladung ({@code join_mode=0}). */
    REQUESTS_DISABLED,
    /** Ignoriert den Einladenden (Spec 01 §6.7 Nr. 2). */
    BLOCKED,
    UNKNOWN_CLAN,
    UNKNOWN_PLAYER,
    NOT_MEMBER,
    /** Keine weitere Stufe ({@code promote}/{@code demote}). */
    NO_FURTHER_ROLE,
    /** {@code clan.err.stats.disabled}. */
    STATS_MODE_DISABLED,
    /** {@code clan.reset.err.notoken}. */
    NO_RESET_TOKEN,
    SELF
}
