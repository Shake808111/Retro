package net.retromc.core.api.stats;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Schlüssel eines Spielmodus wie in {@code stats.mode.key} ({@code VARCHAR(24)}, Spec 02 §2.1), z. B.
 * {@code bedwars} oder {@code bingo_x2}. Wird in Valkey-Schlüsseln ({@code lb:{mode}:30d}) und Nachrichten genutzt.
 */
public record ModeKey(String key) {

    public static final Pattern PATTERN = Pattern.compile("[a-z0-9_]{1,24}");

    // Seed des Modus-Katalogs (Spec 02 §6.7, Migration V2_1__core_ui_stats.sql)
    public static final ModeKey BEDWARS = new ModeKey("bedwars");
    public static final ModeKey SKYWARS = new ModeKey("skywars");
    public static final ModeKey JUMPLEAGUE = new ModeKey("jumpleague");
    public static final ModeKey TTT = new ModeKey("ttt");
    public static final ModeKey CORES = new ModeKey("cores");
    public static final ModeKey PARTYGAMES = new ModeKey("partygames");
    public static final ModeKey SURVIVALGAMES = new ModeKey("survivalgames");
    public static final ModeKey ENDERGAMES = new ModeKey("endergames");
    public static final ModeKey GUNGAME = new ModeKey("gungame");
    public static final ModeKey MASTERBUILDERS = new ModeKey("masterbuilders");
    public static final ModeKey BINGO_X1 = new ModeKey("bingo_x1");
    public static final ModeKey BINGO_X2 = new ModeKey("bingo_x2");
    public static final ModeKey BINGO_X3 = new ModeKey("bingo_x3");
    public static final ModeKey DUELS = new ModeKey("duels");

    public ModeKey {
        Objects.requireNonNull(key, "key");
        if (!PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Ungültiger Modus-Schlüssel: '" + key + "'");
        }
    }

    public static ModeKey of(String key) {
        return new ModeKey(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
