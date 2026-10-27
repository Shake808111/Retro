package net.retromc.core.api.social;

/** Beitrittsmodus {@code social.clan.join_mode} (Spec 01 §2.2, GUI §5.5). */
public enum ClanJoinMode {
    /** 0: nur Einladung. */
    INVITE_ONLY(0),
    /** 1: Einladung und Anfrage ({@code /clan request}). */
    INVITE_AND_REQUEST(1);

    private final int code;

    ClanJoinMode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ClanJoinMode ofCode(int code) {
        for (ClanJoinMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unbekannter Beitrittsmodus: " + code);
    }
}
