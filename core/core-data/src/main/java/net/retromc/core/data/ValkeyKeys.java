package net.retromc.core.data;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Schlüssel-Konventionen für Valkey, übernommen aus Notiz 07, Abschnitt 4
 * ("Valkey key conventions" und Schema-Skizze). Platzhalter {@code {x}} der Notiz werden hier
 * durch den Wert ersetzt (ohne geschweifte Klammern).
 */
public final class ValkeyKeys {

    private ValkeyKeys() {
    }

    /** HASH {proxy, server, since}, TTL 60 s, per Heartbeat erneuert. */
    public static String presence(UUID player) {
        return "presence:" + Objects.requireNonNull(player);
    }

    /** HASH {mode, variant, map, state, online, max}, mit TTL. */
    public static String server(String serverName) {
        return "server:" + requireToken(serverName);
    }

    /** ZSET mit Party- oder Solo-Tickets einer Spielvariante. */
    public static String queue(String variant) {
        return "queue:" + requireToken(variant);
    }

    /** HASH der Party. Partys liegen nur in Valkey (nicht in PostgreSQL). */
    public static String party(String partyId) {
        return "party:" + requireToken(partyId);
    }

    /** SET der Party-Mitglieder. */
    public static String partyMembers(String partyId) {
        return party(partyId) + ":members";
    }

    /** STRING: Party-ID des Spielers. */
    public static String playerParty(UUID player) {
        return "player:" + Objects.requireNonNull(player) + ":party";
    }

    /** ZSET-Bestenliste für alle Zeiten: {@code lb:{mode}:{key}:alltime}. */
    public static String leaderboardAllTime(String mode, String statKey) {
        return "lb:" + requireToken(mode) + ":" + requireToken(statKey) + ":alltime";
    }

    /** ZSET-Bestenliste pro Monat: {@code lb:{mode}:{key}:YYYY-MM}. */
    public static String leaderboardMonthly(String mode, String statKey, YearMonth month) {
        return "lb:" + requireToken(mode) + ":" + requireToken(statKey) + ":" + Objects.requireNonNull(month);
    }

    private static String requireToken(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.contains(":") || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Ungültiger Schlüsselteil: '" + value + "'");
        }
        return value;
    }
}
