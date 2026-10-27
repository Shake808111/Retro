package net.retromc.core.api.social;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Profil-Cache {@code profile:{uuid}} (HASH, einzige Definition Spec 01 §2.3, TTL 10 min). Die Felder
 * {@code coins}, {@code gold}, {@code playtimeS}, {@code statsHidden} pflegt Spec 02.
 *
 * @param rank LuckPerms-Gruppe
 * @param color Namensfarbe als §-Code
 * @param prefix Tab-Präfix oder {@code null}
 * @param locale Sprache, z. B. {@code de_DE}
 * @param level globales Level (Chat-Level, {@code NetworkLevelService})
 * @param nickName aktiver Nick oder {@code null}
 */
public record Profile(
        UUID uuid,
        String name,
        String rank,
        String color,
        @Nullable String prefix,
        String locale,
        @Nullable String clanTag,
        @Nullable Long clanId,
        int level,
        @Nullable String nickName,
        long coins,
        long gold,
        long playtimeS,
        boolean statsHidden) {

    // Feldnamen im HASH (camelCase, Spec 01 §10.1 K5)
    public static final String NAME = "name";
    public static final String RANK = "rank";
    public static final String COLOR = "color";
    public static final String PREFIX = "prefix";
    public static final String LOCALE = "locale";
    public static final String CLAN_TAG = "clanTag";
    public static final String CLAN_ID = "clanId";
    public static final String LEVEL = "level";
    public static final String NICK_NAME = "nickName";
    public static final String COINS = "coins";
    public static final String GOLD = "gold";
    public static final String PLAYTIME_S = "playtimeS";
    public static final String STATS_HIDDEN = "statsHidden";

    public Profile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(locale, "locale");
        if (level < 0 || coins < 0 || gold < 0 || playtimeS < 0) {
            throw new IllegalArgumentException("Negative Profilwerte");
        }
    }

    /** Name, unter dem andere den Spieler sehen (Nick, sonst echter Name). */
    public String visibleName() {
        return nickName != null ? nickName : name;
    }

    /** HASH-Felder; {@code null}-Werte fehlen, Booleans als {@code 0/1}. */
    public Map<String, String> toHash() {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(NAME, name);
        hash.put(RANK, rank);
        hash.put(COLOR, color);
        if (prefix != null) {
            hash.put(PREFIX, prefix);
        }
        hash.put(LOCALE, locale);
        if (clanTag != null) {
            hash.put(CLAN_TAG, clanTag);
        }
        if (clanId != null) {
            hash.put(CLAN_ID, Long.toString(clanId));
        }
        hash.put(LEVEL, Integer.toString(level));
        if (nickName != null) {
            hash.put(NICK_NAME, nickName);
        }
        hash.put(COINS, Long.toString(coins));
        hash.put(GOLD, Long.toString(gold));
        hash.put(PLAYTIME_S, Long.toString(playtimeS));
        hash.put(STATS_HIDDEN, statsHidden ? "1" : "0");
        return hash;
    }

    /** Liest einen HASH; fehlende Zahlen gelten als 0, leere Texte als {@code null}. */
    public static Profile fromHash(UUID uuid, Map<String, String> hash) {
        return new Profile(
                uuid,
                required(hash, NAME),
                required(hash, RANK),
                required(hash, COLOR),
                optional(hash, PREFIX),
                required(hash, LOCALE),
                optional(hash, CLAN_TAG),
                optional(hash, CLAN_ID) == null ? null : Long.valueOf(hash.get(CLAN_ID)),
                (int) number(hash, LEVEL),
                optional(hash, NICK_NAME),
                number(hash, COINS),
                number(hash, GOLD),
                number(hash, PLAYTIME_S),
                "1".equals(hash.get(STATS_HIDDEN)));
    }

    private static @Nullable String optional(Map<String, String> hash, String field) {
        String value = hash.get(field);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String required(Map<String, String> hash, String field) {
        String value = optional(hash, field);
        if (value == null) {
            throw new IllegalArgumentException("Feld fehlt in profile-Hash: " + field);
        }
        return value;
    }

    private static long number(Map<String, String> hash, String field) {
        String value = optional(hash, field);
        return value == null ? 0 : Long.parseLong(value);
    }
}
