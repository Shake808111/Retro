package net.retromc.core.api.server;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Eintrag der Server-Registry, Valkey-Hash {@code server:{name}} (Spec 08 §2.2, §2.3). Felder 1:1 als HASH,
 * Zahlen als Dezimal-Strings, Booleans als {@code 0/1}. Der Heartbeat schreibt zusätzlich {@code build} und die
 * Zusatzfelder des Spiel-Rahmens ({@code countdown}, {@code spectatable}, {@code teams}, {@code teamSize},
 * {@code privateId}, {@code owner}); diese gehören nicht zu diesem Record und werden beim Lesen ignoriert.
 *
 * @param template Template-ID ({@code lobby}, {@code bedwars-4x4}, …)
 * @param mode Modus ({@code lobby} bei der Hauptlobby, Modus-Lobby = Kategorie LOBBY mit Modus ≠ {@code lobby})
 * @param variant Variante, Standard {@code default}
 * @param max Höchstzahl Spieler ({@code maxPlayers} des Templates)
 * @param reserved Reservierungen ({@code ZCOUNT reserve:{name} now +inf})
 * @param map aktuelle Map oder {@code null}
 * @param minProtocol kleinste Protokollnummer (Versionsprüfung Velocity), 0 = keine
 * @param rankMin Mindest-Rangewicht (z. B. Premium-Lobby 200)
 * @param displayName Anzeigename des Templates (Basis von {@link #serverDisplay()})
 * @param versionLabel Versionstext für Navigator/Private Runden oder {@code null}
 * @param matchId laufende Runde oder {@code null}
 */
public record ServerInfo(
        String name,
        String template,
        String mode,
        String variant,
        ServerCategory category,
        ServerState state,
        int online,
        int max,
        int reserved,
        @Nullable String map,
        String host,
        int port,
        int minProtocol,
        boolean silent,
        int rankMin,
        boolean privateRound,
        String displayName,
        @Nullable String versionLabel,
        @Nullable String matchId,
        Instant startedAt) {

    public static final String F_NAME = "name";
    public static final String F_TEMPLATE = "template";
    public static final String F_MODE = "mode";
    public static final String F_VARIANT = "variant";
    public static final String F_CATEGORY = "category";
    public static final String F_STATE = "state";
    public static final String F_ONLINE = "online";
    public static final String F_MAX = "max";
    public static final String F_RESERVED = "reserved";
    public static final String F_MAP = "map";
    public static final String F_HOST = "host";
    public static final String F_PORT = "port";
    public static final String F_MIN_PROTOCOL = "minProtocol";
    public static final String F_JOINABLE = "joinable";
    public static final String F_SILENT = "silent";
    public static final String F_RANK_MIN = "rankMin";
    public static final String F_PRIVATE_ROUND = "privateRound";
    public static final String F_VERSION_LABEL = "versionLabel";
    public static final String F_DISPLAY_NAME = "displayName";
    public static final String F_MATCH_ID = "matchId";
    public static final String F_STARTED_AT = "startedAt";
    public static final String F_BUILD = "build";

    /** Variante, wenn das Template keine angibt (Spec 08 §6.5). */
    public static final String DEFAULT_VARIANT = "default";

    public ServerInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(startedAt, "startedAt");
        if (online < 0 || max < 0 || reserved < 0 || minProtocol < 0 || rankMin < 0) {
            throw new IllegalArgumentException("Zahlenfelder dürfen nicht negativ sein");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port außerhalb 1..65535: " + port);
        }
    }

    /** Freie Plätze {@code max(0, max − online − reserved)} (Spec 08 §2.3). */
    public int freeSlots() {
        return Math.max(0, max - online - reserved);
    }

    /**
     * Beitretbar nach Spec 08 §6.7: LOBBY (und PERSISTENT) {@code state == ONLINE && online < max} (DRAINING ist
     * ein eigener Zustand); GAME {@code state ∈ {WAITING, STARTING} && online + reserved < max && !privateRound}.
     */
    public boolean joinable() {
        return switch (category) {
            case LOBBY, PERSISTENT -> state == ServerState.ONLINE && online < max;
            case GAME -> (state == ServerState.WAITING || state == ServerState.STARTING)
                    && online + reserved < max && !privateRound;
        };
    }

    /**
     * Servername für Tablist-Header und Scoreboard (Spec 08 §4): Lobbys {@code {displayName}-{n}}
     * ({@code lobby} ohne Zahl → {@code -1}), Spiel- und Dauerserver {@code {displayName}}.
     */
    public String serverDisplay() {
        if (category != ServerCategory.LOBBY) {
            return displayName;
        }
        return displayName + "-" + TemplateId.trailingNumber(name).orElse(1);
    }

    /** Ob dies eine Modus-Lobby ist (Kategorie LOBBY mit Modus ≠ {@code lobby}, Spec 02 §8.4). */
    public boolean isModeLobby() {
        return category == ServerCategory.LOBBY && !"lobby".equals(mode);
    }

    /** HASH-Felder für {@code server:{name}} inklusive {@code joinable}; {@code null}-Felder fehlen. */
    public Map<String, String> toHash() {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(F_NAME, name);
        hash.put(F_TEMPLATE, template);
        hash.put(F_MODE, mode);
        hash.put(F_VARIANT, variant);
        hash.put(F_CATEGORY, category.name());
        hash.put(F_STATE, state.name());
        hash.put(F_ONLINE, Integer.toString(online));
        hash.put(F_MAX, Integer.toString(max));
        hash.put(F_RESERVED, Integer.toString(reserved));
        if (map != null) {
            hash.put(F_MAP, map);
        }
        hash.put(F_HOST, host);
        hash.put(F_PORT, Integer.toString(port));
        hash.put(F_MIN_PROTOCOL, Integer.toString(minProtocol));
        hash.put(F_JOINABLE, bit(joinable()));
        hash.put(F_SILENT, bit(silent));
        hash.put(F_RANK_MIN, Integer.toString(rankMin));
        hash.put(F_PRIVATE_ROUND, bit(privateRound));
        if (versionLabel != null) {
            hash.put(F_VERSION_LABEL, versionLabel);
        }
        hash.put(F_DISPLAY_NAME, displayName);
        if (matchId != null) {
            hash.put(F_MATCH_ID, matchId);
        }
        hash.put(F_STARTED_AT, Long.toString(startedAt.toEpochMilli()));
        return hash;
    }

    /** Liest einen HASH; {@code joinable} wird neu berechnet, unbekannte Felder werden ignoriert. */
    public static ServerInfo fromHash(Map<String, String> hash) {
        return new ServerInfo(
                required(hash, F_NAME),
                required(hash, F_TEMPLATE),
                required(hash, F_MODE),
                hash.getOrDefault(F_VARIANT, DEFAULT_VARIANT),
                ServerCategory.valueOf(required(hash, F_CATEGORY)),
                ServerState.valueOf(required(hash, F_STATE)),
                number(hash, F_ONLINE),
                number(hash, F_MAX),
                number(hash, F_RESERVED),
                hash.get(F_MAP),
                required(hash, F_HOST),
                Integer.parseInt(required(hash, F_PORT)),
                number(hash, F_MIN_PROTOCOL),
                "1".equals(hash.get(F_SILENT)),
                number(hash, F_RANK_MIN),
                "1".equals(hash.get(F_PRIVATE_ROUND)),
                required(hash, F_DISPLAY_NAME),
                hash.get(F_VERSION_LABEL),
                hash.get(F_MATCH_ID),
                Instant.ofEpochMilli(Long.parseLong(required(hash, F_STARTED_AT))));
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static int number(Map<String, String> hash, String field) {
        String value = hash.get(field);
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static String required(Map<String, String> hash, String field) {
        String value = hash.get(field);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Feld fehlt in server-Hash: " + field);
        }
        return value;
    }
}
