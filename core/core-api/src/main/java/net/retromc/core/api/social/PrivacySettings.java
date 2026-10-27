package net.retromc.core.api.social;

/**
 * Zeile aus {@code social.privacy} (V1 + V1_1, Spec 01 §2.2, Werte §6.13) mit den Prüfregeln aus §6.4–§6.7.
 *
 * @param friendRequests Master-Schalter für Freundschaftsanfragen
 * @param friendReqFrom 0 niemand, 1 ab Premium, 2 alle
 * @param msgFrom 0 niemand, 1 Freunde, 2 alle
 * @param partyFrom 0 niemand, 1 Freunde, 2 alle
 * @param showOnline Online-Status sichtbar
 * @param jumpFrom 0 niemand, 1 Freunde, 2 nur Favoriten
 */
public record PrivacySettings(
        boolean friendRequests,
        int friendReqFrom,
        int msgFrom,
        int partyFrom,
        boolean showOnline,
        int jumpFrom,
        boolean notifyFriends,
        boolean notifyClan,
        boolean hideFromClan,
        boolean globalChat,
        boolean nickOnJoin,
        boolean nickKeep) {

    /** Gewicht, ab dem der Filter „Ab Premium“ greift (Spec 01 §6.4 Nr. 1). */
    public static final int PREMIUM_WEIGHT = 200;

    /** Standardwerte Spec 01 §6.13 (Partyeinladungen: 2 = alle). */
    public static final PrivacySettings DEFAULTS =
            new PrivacySettings(true, 2, 1, 2, true, 1, true, true, false, true, false, false);

    public PrivacySettings {
        check("friendReqFrom", friendReqFrom);
        check("msgFrom", msgFrom);
        check("partyFrom", partyFrom);
        check("jumpFrom", jumpFrom);
    }

    private static void check(String field, int value) {
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException(field + " muss 0, 1 oder 2 sein: " + value);
        }
    }

    /** Wert einer Einstellung (Schalter 1/0). */
    public int value(Setting setting) {
        return switch (setting) {
            case FRIEND_REQUESTS -> friendRequests ? friendReqFrom : 0;
            case SHOW_ONLINE -> bit(showOnline);
            case MSG_FROM -> msgFrom;
            case PARTY_FROM -> partyFrom;
            case JUMP_FROM -> jumpFrom;
            case NOTIFY_FRIENDS -> bit(notifyFriends);
            case NOTIFY_CLAN -> bit(notifyClan);
            case HIDE_FROM_CLAN -> bit(hideFromClan);
            case GLOBAL_CHAT -> bit(globalChat);
            case NICK_ON_JOIN -> bit(nickOnJoin);
            case NICK_KEEP -> bit(nickKeep);
        };
    }

    /** Kopie mit geändertem Wert; {@code FRIEND_REQUESTS=0} schaltet nur den Master-Schalter aus. */
    public PrivacySettings with(Setting setting, int value) {
        if (!setting.allows(value)) {
            throw new IllegalArgumentException("Wert " + value + " ist für " + setting + " nicht erlaubt");
        }
        boolean on = value == 1;
        return switch (setting) {
            case FRIEND_REQUESTS -> value == 0
                    ? copy(false, friendReqFrom, msgFrom, partyFrom, showOnline, jumpFrom)
                    : copy(true, value, msgFrom, partyFrom, showOnline, jumpFrom);
            case SHOW_ONLINE -> copy(friendRequests, friendReqFrom, msgFrom, partyFrom, on, jumpFrom);
            case MSG_FROM -> copy(friendRequests, friendReqFrom, value, partyFrom, showOnline, jumpFrom);
            case PARTY_FROM -> copy(friendRequests, friendReqFrom, msgFrom, value, showOnline, jumpFrom);
            case JUMP_FROM -> copy(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline, value);
            case NOTIFY_FRIENDS -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, on, notifyClan, hideFromClan, globalChat, nickOnJoin, nickKeep);
            case NOTIFY_CLAN -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, notifyFriends, on, hideFromClan, globalChat, nickOnJoin, nickKeep);
            case HIDE_FROM_CLAN -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, notifyFriends, notifyClan, on, globalChat, nickOnJoin, nickKeep);
            case GLOBAL_CHAT -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, notifyFriends, notifyClan, hideFromClan, on, nickOnJoin, nickKeep);
            case NICK_ON_JOIN -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, notifyFriends, notifyClan, hideFromClan, globalChat, on, nickKeep);
            case NICK_KEEP -> new PrivacySettings(friendRequests, friendReqFrom, msgFrom, partyFrom, showOnline,
                    jumpFrom, notifyFriends, notifyClan, hideFromClan, globalChat, nickOnJoin, on);
        };
    }

    /** Nächster Wert im GUI-Zyklus. */
    public PrivacySettings cycle(Setting setting) {
        return with(setting, setting.next(value(setting)));
    }

    /**
     * {@code /friend toggle}: nur den Master-Schalter umlegen. Beim Einschalten bleibt der Filter erhalten
     * (war er „niemand“, gilt wieder „alle“).
     */
    public PrivacySettings toggleFriendRequests() {
        if (friendRequests) {
            return copy(false, friendReqFrom, msgFrom, partyFrom, showOnline, jumpFrom);
        }
        return copy(true, friendReqFrom == 0 ? 2 : friendReqFrom, msgFrom, partyFrom, showOnline, jumpFrom);
    }

    /** Nimmt der Spieler eine Anfrage eines Absenders mit diesem Ranggewicht an (Spec 01 §6.4 Nr. 1)? */
    public boolean acceptsFriendRequestFrom(int senderWeight) {
        return friendRequests && switch (friendReqFrom) {
            case 2 -> true;
            case 1 -> senderWeight >= PREMIUM_WEIGHT;
            default -> false;
        };
    }

    /** Private Nachricht erlaubt? Team darf immer (Spec 01 §6.7 Nr. 1). */
    public boolean acceptsMessageFrom(boolean friend, boolean team) {
        return team || allowedBy(msgFrom, friend);
    }

    /** Party-Einladung erlaubt (Spec 01 §3.3 #23)? */
    public boolean acceptsPartyInviteFrom(boolean friend) {
        return allowedBy(partyFrom, friend);
    }

    /**
     * Darf ein Freund nachspringen ({@code /friend jump})?
     *
     * @param favoriteWithJump der Spieler hat den Springenden als Favorit mit {@code allow_jump} markiert
     */
    public boolean allowsJumpFrom(boolean friend, boolean favoriteWithJump) {
        return switch (jumpFrom) {
            case 1 -> friend;
            case 2 -> friend && favoriteWithJump;
            default -> false;
        };
    }

    private static boolean allowedBy(int level, boolean friend) {
        return level == 2 || level == 1 && friend;
    }

    private PrivacySettings copy(boolean requests, int reqFrom, int msg, int party, boolean online, int jump) {
        return new PrivacySettings(requests, reqFrom, msg, party, online, jump, notifyFriends, notifyClan,
                hideFromClan, globalChat, nickOnJoin, nickKeep);
    }

    private static int bit(boolean value) {
        return value ? 1 : 0;
    }
}
