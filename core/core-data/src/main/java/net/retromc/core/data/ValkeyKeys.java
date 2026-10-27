package net.retromc.core.data;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.util.IpHmac;
import net.retromc.core.api.util.Ulid;

/**
 * Schlüssel-Konventionen für Valkey {@code <bereich>:<id>[:<teil>]} (Spec 00 §4.2). Quellen: Spec 01 §2.3
 * (Präsenz, Profil, Social, Moderation), Spec 02 §2.2 (Statistik), Spec 08 §2.2 (Registry, Controller, Discord, API).
 * Die TTL-Konstanten stehen neben ihrem Schlüssel. Platzhalter {@code {x}} der Specs werden ohne Klammern ersetzt.
 */
public final class ValkeyKeys {

    /** Schlüsselteile: nicht leer, ohne Doppelpunkt und ohne Leerraum. */
    private static final Pattern TOKEN = Pattern.compile("[^:\\s]+");

    private ValkeyKeys() {
    }

    // -----------------------------------------------------------------------------------------------------------
    // Spec 01 §2.3 – Präsenz, Profil, Social, Moderation
    // -----------------------------------------------------------------------------------------------------------

    /** Lebensdauer von {@code presence:{uuid}}. */
    public static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    /** Heartbeat der Präsenz (Velocity). */
    public static final Duration PRESENCE_HEARTBEAT = Duration.ofSeconds(20);
    public static final Duration PROFILE_TTL = Duration.ofMinutes(10);
    public static final Duration NAME_TTL = Duration.ofHours(24);
    public static final Duration PARTY_TTL = Duration.ofMinutes(30);
    public static final Duration PARTY_REQUEST_TTL = Duration.ofSeconds(60);
    public static final Duration PARTY_INVITE_TTL = Duration.ofSeconds(60);
    public static final Duration PARTY_GRACE_TTL = Duration.ofSeconds(300);
    public static final Duration MSG_LAST_TTL = Duration.ofMinutes(30);
    public static final Duration MSG_RECENT_TTL = Duration.ofHours(24);
    public static final Duration IGNORE_TTL = Duration.ofMinutes(10);
    public static final Duration BAN_NONE_TTL = Duration.ofSeconds(60);
    public static final Duration SPAM_TTL = Duration.ofSeconds(30);
    public static final Duration REPORT_TARGET_COOLDOWN_TTL = Duration.ofSeconds(300);
    public static final Duration REPORT_HOURLY_TTL = Duration.ofSeconds(3600);
    public static final Duration DAILY_COUNTER_TTL = Duration.ofHours(26);
    public static final Duration CHAT_BUFFER_TTL = Duration.ofMinutes(15);

    /** HASH {proxy, server, since, afk, nick, hidden}, TTL 60 s, Heartbeat 20 s. */
    public static String presence(UUID player) {
        return "presence:" + Objects.requireNonNull(player);
    }

    /** HASH des Profil-Caches (einzige Definition Spec 01 §2.3), TTL 10 min. */
    public static String profile(UUID player) {
        return "profile:" + Objects.requireNonNull(player);
    }

    /** STRING uuid zu einem Namen in Kleinbuchstaben ({@code name:{lower}}), TTL 24 h. */
    public static String playerName(String name) {
        return "name:" + token(name).toLowerCase(Locale.ROOT);
    }

    /** HASH der Party {leader, created, public, publicFilter, requireRequest, maxSize}; Party-ID = ULID. */
    public static String party(String partyId) {
        return "party:" + partyId(partyId);
    }

    /** ZSET der Mitglieder: uuid → Beitritt (ms), Reihenfolge = Nachfolge. */
    public static String partyMembers(String partyId) {
        return party(partyId) + ":members";
    }

    /** ZSET der Beitrittsanfragen: uuid → Anfragezeit (60 s je Eintrag). */
    public static String partyRequests(String partyId) {
        return party(partyId) + ":requests";
    }

    /** STRING: Party-ID des Spielers. */
    public static String playerParty(UUID player) {
        return "player:" + Objects.requireNonNull(player) + ":party";
    }

    /** HASH partyId → {@code inviter;expiresMs}, TTL 60 s. */
    public static String partyInvites(UUID target) {
        return "party:invite:" + Objects.requireNonNull(target);
    }

    /** ZSET öffentlicher Partys: partyId → Erstellzeit. */
    public static String partyPublic() {
        return "party:public";
    }

    /** STRING partyId während der Offline-Karenz, TTL 300 s. */
    public static String partyGrace(UUID player) {
        return "party:grace:" + Objects.requireNonNull(player);
    }

    /** STRING uuid des letzten PN-Partners, TTL 30 min. */
    public static String msgLast(UUID player) {
        return "msg:last:" + Objects.requireNonNull(player);
    }

    /** LIST der letzten 10 empfangenen PN (JSON {from,text,ts}), TTL 24 h. */
    public static String msgRecent(UUID player) {
        return "msg:recent:" + Objects.requireNonNull(player);
    }

    /** SET ignorierter Spieler (Cache von {@code social.ignore}), TTL 10 min. */
    public static String ignore(UUID player) {
        return "ignore:" + Objects.requireNonNull(player);
    }

    /** HASH {publicId, reasonCode, expiresMs (-1 perm)}, TTL = Restlaufzeit. */
    public static String mute(UUID player) {
        return "mute:" + Objects.requireNonNull(player);
    }

    /** STRING „1“ = geprüft, kein Bann (Negativ-Cache), TTL 60 s. */
    public static String banNone(UUID player) {
        return "ban:none:" + Objects.requireNonNull(player);
    }

    /** HASH {name, texture, signature, since} des aktiven Nicks. */
    public static String nick(UUID player) {
        return "nick:" + Objects.requireNonNull(player);
    }

    /** STRING uuid zum Nick-Namen (Kollisionsschutz). */
    public static String nickName(String nick) {
        return "nick:name:" + token(nick).toLowerCase(Locale.ROOT);
    }

    /** HASH {lastText, lastAt, repeatCount, burst} des Anti-Spam, TTL 30 s. */
    public static String spam(UUID player) {
        return "spam:" + Objects.requireNonNull(player);
    }

    /** STRING „1“: gleiches Ziel erst nach 300 s wieder meldbar. */
    public static String reportCooldown(UUID reporter, UUID target) {
        return "cd:report:" + Objects.requireNonNull(reporter) + ":" + Objects.requireNonNull(target);
    }

    /** STRING (INCR): Meldungen je Stunde, TTL 3600 s. */
    public static String reportHourly(UUID reporter) {
        return "cd:report:" + Objects.requireNonNull(reporter);
    }

    /** STRING (INCR): {@code /joinme} je Tag (Europe/Berlin), TTL 26 h. */
    public static String joinmeCount(UUID player, LocalDate day) {
        return "cnt:joinme:" + Objects.requireNonNull(player) + ":" + Objects.requireNonNull(day);
    }

    /** STRING (INCR): Clan-Umbenennungen je Tag (Europe/Berlin), TTL 26 h. */
    public static String clanRenameCount(long clanId, LocalDate day) {
        return "cnt:clanrename:" + clanId + ":" + Objects.requireNonNull(day);
    }

    /** LIST der letzten 200 Chatzeilen (JSON) eines Servers für {@code /chatlog}, TTL 15 min. */
    public static String chatBuffer(String server) {
        return "chatbuf:" + serverName(server);
    }

    /** HASH {enabled, reason, since} des Wartungsmodus. */
    public static String maintenance() {
        return "maintenance";
    }

    // -----------------------------------------------------------------------------------------------------------
    // Spec 02 §2.2 – Statistik
    // -----------------------------------------------------------------------------------------------------------

    public static final Duration LEADERBOARD_TMP_TTL = Duration.ofSeconds(600);
    public static final Duration STATS_CACHE_TTL = Duration.ofSeconds(60);
    public static final Duration TOP_CACHE_TTL = Duration.ofSeconds(30);
    public static final Duration STATS_RATE_LIMIT_TTL = Duration.ofMillis(1000);
    public static final Duration ROLLOVER_LOCK_TTL = Duration.ofMinutes(10);
    public static final Duration RECONCILE_LOCK_TTL = Duration.ofMinutes(60);
    public static final Duration RESET_CONFIRM_TTL = Duration.ofSeconds(30);

    /** ZSET-Bestenliste {@code lb:{mode}:30d} bzw. {@code lb:{mode}:all}; Member = UUID, Score = Ranking-Score. */
    public static String leaderboard(ModeKey mode, StatPeriod period) {
        return "lb:" + Objects.requireNonNull(mode).key() + ":" + Objects.requireNonNull(period).key();
    }

    /** ZSET für den Neuaufbau, danach {@code RENAME}, TTL 600 s. */
    public static String leaderboardTmp(ModeKey mode, StatPeriod period) {
        return leaderboard(mode, period) + ":tmp";
    }

    /** HASH-Cache der {@code /stats}-Werte, TTL 60 s. */
    public static String statsCache(ModeKey mode, UUID player, StatPeriod period) {
        return "st:" + Objects.requireNonNull(mode).key() + ":" + Objects.requireNonNull(player) + ":"
                + Objects.requireNonNull(period).key();
    }

    /** STRING (JSON) der Top 10, TTL 30 s. */
    public static String topCache(ModeKey mode, StatPeriod period) {
        return "top:" + Objects.requireNonNull(mode).key() + ":" + Objects.requireNonNull(period).key();
    }

    /** STRING (INCR, PEXPIRE 1000 ms): 1 Abfrage pro Sekunde. */
    public static String statsRateLimit(UUID player) {
        return "rl:stats:" + Objects.requireNonNull(player);
    }

    /** {@code SET NX PX 600000}: ein Rollover pro Tag im ganzen Netzwerk. */
    public static String statsRolloverLock(LocalDate day) {
        return "lock:stats:rollover:" + Objects.requireNonNull(day);
    }

    /** {@code SET NX PX 3600000}: nächtlicher Neuaufbau. */
    public static String statsReconcileLock(LocalDate day) {
        return "lock:stats:reconcile:" + Objects.requireNonNull(day);
    }

    /** STRING (mode) des offenen Reset-Dialogs, TTL 30 s. */
    public static String resetConfirm(UUID player) {
        return "reset:confirm:" + Objects.requireNonNull(player);
    }

    /** LIST (JSON {color,title,url}) des Lobby-Newstickers. */
    public static String uiNews() {
        return "ui:news";
    }

    // -----------------------------------------------------------------------------------------------------------
    // Spec 08 §2.2 – Registry, Controller, Queue, Discord, API
    // -----------------------------------------------------------------------------------------------------------

    /** TTL von {@code server:{name}} und {@code proxy:{id}}; Heartbeat alle 5 s. */
    public static final Duration SERVER_TTL = Duration.ofSeconds(15);
    public static final Duration SERVER_HEARTBEAT = Duration.ofSeconds(5);
    public static final Duration RESERVE_TTL = Duration.ofSeconds(30);
    public static final Duration ONLINE_COUNT_TTL = Duration.ofSeconds(15);
    public static final Duration CONTROLLER_LEADER_TTL = Duration.ofSeconds(10);
    public static final Duration CONTROLLER_LEADER_RENEW = Duration.ofSeconds(3);
    public static final Duration TICKET_TTL = Duration.ofSeconds(60);
    public static final Duration DISCORD_CODE_TTL = Duration.ofSeconds(600);
    public static final Duration DISCORD_RATE_LIMIT_TTL = Duration.ofSeconds(600);
    public static final int DISCORD_CODES_PER_WINDOW = 5;
    public static final Duration API_RATE_LIMIT_TTL = Duration.ofSeconds(60);
    public static final int API_REQUESTS_PER_WINDOW = 60;
    public static final Duration API_CACHE_TTL = Duration.ofSeconds(30);

    /** HASH der Registry ({@code ServerInfo.toHash()} + Zusatzfelder), TTL 15 s. */
    public static String server(String serverName) {
        return "server:" + serverName(serverName);
    }

    /** SET aller Servernamen. */
    public static String serversAll() {
        return "servers:all";
    }

    /** SET der Servernamen eines Templates. */
    public static String serversByTemplate(TemplateId template) {
        return "servers:tpl:" + Objects.requireNonNull(template).value();
    }

    /** ZSET uuid → expiresAtMillis der Reservierungen eines Spielservers, TTL 30 s. */
    public static String reserve(String serverName) {
        return "reserve:" + serverName(serverName);
    }

    /** HASH {online, startedAt, build} eines Proxys, TTL 15 s. */
    public static String proxy(String proxyId) {
        return "proxy:" + token(proxyId);
    }

    /** SET der Proxy-IDs. */
    public static String proxies() {
        return "proxies";
    }

    /** STRING Summe {@code online} aller Proxys, TTL 15 s. */
    public static String onlineCount() {
        return "count:online";
    }

    /** STRING Instanz-ID des aktiven Controllers ({@code SET NX PX 10000}). */
    public static String controllerLeader() {
        return "ctl:leader";
    }

    /** SET belegter laufender Nummern eines Templates. */
    public static String controllerNames(TemplateId template) {
        return "ctl:names:" + Objects.requireNonNull(template).value();
    }

    /** HASH {minReady, max} aus {@code /cloud scale} bis {@code /cloud reload}. */
    public static String controllerOverride(TemplateId template) {
        return "ctl:override:" + Objects.requireNonNull(template).value();
    }

    /** HASH eines Queue-Tickets {players, mode, variant, partyId, createdAt, state, source}, TTL 60 s. */
    public static String ticket(String ticketId) {
        return "ticket:" + token(ticketId);
    }

    /** ZSET der Tickets einer Spielvariante (bestehend, Spec 08 §2.2). */
    public static String queue(String variant) {
        return "queue:" + token(variant);
    }

    /** HASH mapId → aktive Version eines Modus. */
    public static String maps(String mode) {
        return "maps:" + ModeKey.of(mode).key();
    }

    /** HASH {at, reason, actor} eines geplanten Neustarts. */
    public static String restart(String serverName) {
        return "restart:" + serverName(serverName);
    }

    /** STRING uuid zum Discord-Code (6 Zeichen), TTL 600 s. */
    public static String discordCode(String code) {
        return "discord:code:" + token(code);
    }

    /** STRING offener Code des Spielers, TTL 600 s. */
    public static String discordPending(UUID player) {
        return "discord:pending:" + Objects.requireNonNull(player);
    }

    /** STRING Zähler, höchstens 5 Codes in 600 s. */
    public static String discordCodeRateLimit(UUID player) {
        return "rl:discordcode:" + Objects.requireNonNull(player);
    }

    /** STRING Zähler je IP-HMAC, höchstens 60 Anfragen in 60 s. */
    public static String apiRateLimit(IpHmac ip) {
        return "rl:api:" + Objects.requireNonNull(ip).hex();
    }

    /** STRING (JSON) Antwort-Cache eines API-Pfads, TTL 30 s. */
    public static String apiCache(String path) {
        return "cache:api:" + token(path);
    }

    // -----------------------------------------------------------------------------------------------------------

    private static String token(String value) {
        Objects.requireNonNull(value);
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Ungültiger Schlüsselteil: '" + value + "'");
        }
        return value;
    }

    private static String serverName(String name) {
        Objects.requireNonNull(name);
        if (!TemplateId.SERVER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Ungültiger Servername: '" + name + "'");
        }
        return name;
    }

    private static String partyId(String partyId) {
        if (!Ulid.isValid(partyId)) {
            throw new IllegalArgumentException("Party-ID muss eine ULID sein: '" + partyId + "'");
        }
        return partyId;
    }
}
