package net.retromc.core.api.social;

import java.time.Duration;

/** Zahlen der Clan-Mechanik (Spec 01 §6.6, §10 O3/O15/O16). */
public final class ClanRules {

    /** Basisgröße; dazu der Bonus nach Rang des Leiters ({@code RankLimits.clanSlotBonus}). */
    public static final int BASE_MEMBERS = 30;

    /** Umbenennungen je Tag (Europe/Berlin), Zähler {@code cnt:clanrename:{clanId}:{yyyy-mm-dd}}. */
    public static final int RENAMES_PER_DAY = 2;

    /** Clan wird gelöscht, wenn {@code last_active} älter ist (Job täglich 04:00). */
    public static final Duration INACTIVE_AFTER = Duration.ofDays(60);

    /** Gültigkeit einer Einladung (Annahme). */
    public static final Duration INVITE_TTL = Duration.ofDays(7);

    /** Clan-Wars je Clan, Modus und Tag (O15). */
    public static final int WARS_PER_DAY = 24;

    /** Clan-XP: Ranked-Sieg, Ranked-Niederlage, FunClanWar (O16). */
    public static final int XP_RANKED_WIN = 100;
    public static final int XP_RANKED_LOSS = 25;
    public static final int XP_FUN = 10;

    /** Kosten von {@code /clanstatsreset} in ResetTokens. */
    public static final int RESET_TOKEN_COST = 1;

    private ClanRules() {
    }

    /** Höchstzahl Mitglieder: 30 + Bonus des Leiter-Rangs. */
    public static int maxMembers(int leaderClanSlotBonus) {
        if (leaderClanSlotBonus < 0) {
            throw new IllegalArgumentException("Bonus darf nicht negativ sein");
        }
        return BASE_MEMBERS + leaderClanSlotBonus;
    }

    /** XP für einen Clan-War aus Sicht eines Clans. */
    public static int warXp(boolean ranked, boolean won) {
        if (!ranked) {
            return XP_FUN;
        }
        return won ? XP_RANKED_WIN : XP_RANKED_LOSS;
    }

    /** Ob noch ein Umbenennen erlaubt ist, nachdem heute {@code renamesToday} erfolgt sind. */
    public static boolean renameAllowed(int renamesToday) {
        return renamesToday < RENAMES_PER_DAY;
    }
}
