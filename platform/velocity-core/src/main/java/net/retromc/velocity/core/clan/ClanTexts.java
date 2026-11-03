package net.retromc.velocity.core.clan;

import java.util.List;
import net.retromc.core.api.social.ClanOutcome;
import net.retromc.core.api.stats.ModeKey;

/** Zuordnung der Clan-Ergebnisse zu Texten (Spec 01 §3.4, §7 {@code clan.*}). */
final class ClanTexts {

    /** Modi der Moduswahl von {@code /clan cwstats} (Clan-War-Modi). */
    static final List<ModeKey> WAR_MODES = List.of(ModeKey.BEDWARS, ModeKey.SKYWARS, ModeKey.SURVIVALGAMES,
            ModeKey.CORES, ModeKey.TTT, ModeKey.ENDERGAMES, ModeKey.GUNGAME, ModeKey.MASTERBUILDERS);

    private ClanTexts() {
    }

    /** Fehlertext eines Ergebnisses; Erfolge behandelt der jeweilige Befehl. */
    static String error(ClanOutcome outcome) {
        return switch (outcome) {
            case INVALID_NAME -> "clan.err.name";
            case INVALID_TAG -> "clan.err.tag";
            case NAME_TAKEN -> "clan.err.name.taken";
            case TAG_TAKEN -> "clan.err.tag.taken";
            case IN_CLAN -> "clan.err.inclan";
            case NOT_IN_CLAN -> "clan.err.notinclan";
            case TARGET_IN_CLAN -> "clan.err.target.inclan";
            case FULL -> "clan.err.full";
            case NOT_ALLOWED -> "clan.err.notallowed";
            case NOT_LEADER -> "clan.err.notleader";
            case LEADER_CANNOT_LEAVE -> "clan.err.leader.leave";
            case RENAME_LIMIT -> "clan.err.rename.limit";
            case NO_INVITE -> "clan.err.noinvite";
            case ALREADY_INVITED -> "clan.err.invited";
            case NO_REQUEST -> "clan.err.norequest";
            case ALREADY_REQUESTED -> "clan.err.requested";
            case REQUESTS_DISABLED -> "clan.err.requests.off";
            case BLOCKED -> "clan.err.denied";
            case UNKNOWN_CLAN -> "clan.err.unknown";
            case UNKNOWN_PLAYER -> "error.player.unknown";
            case NOT_MEMBER -> "clan.err.notmember";
            case NO_FURTHER_ROLE -> "clan.err.norole";
            case STATS_MODE_DISABLED -> "clan.err.stats.disabled";
            case NO_RESET_TOKEN -> "clan.reset.err.notoken";
            case SELF -> "clan.err.self";
            case CREATED, INVITED, JOINED, REQUEST_SENT, REQUEST_DENIED, LEFT, KICKED, PROMOTED, DEMOTED,
                    LEADER_TRANSFERRED, CONFIRM_TRANSFER, RENAMED, DELETED, UPDATED, STATS_ENABLED, STATS_DISABLED,
                    STATS_RESET -> throw new IllegalArgumentException("Kein Fehler: " + outcome);
        };
    }
}
