package net.retromc.core.api.stats;

/** Art eines Statistik-Resets ({@code stats.reset_log.scope}, Spec 02 §2.1, §6.9). */
public enum ResetScope {
    /** Spieler mit ResetToken ({@code /resetstats}). */
    PLAYER("player"),
    /** Team ohne Token ({@code /statsadmin reset}). */
    STAFF("staff"),
    /** Clan-Reset (Clan-Spec). */
    CLAN("clan");

    private final String key;

    ResetScope(String key) {
        this.key = key;
    }

    /** Wert in {@code stats.reset_log.scope}. */
    public String key() {
        return key;
    }

    /** Verbraucht der Reset einen Token? */
    public boolean consumesToken() {
        return this == PLAYER;
    }
}
