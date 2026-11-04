package net.retromc.platform.common.config;

/** Namen der Umgebungsvariablen der Kern-Plugins (Spec 08 §6.15). */
public final class EnvKeys {

    /** Servername bzw. Proxy-ID, z. B. {@code lobby-1}, {@code proxy-1} (Pflicht). */
    public static final String SERVER_NAME = "NETWORK_SERVER_NAME";
    /** Template-ID, z. B. {@code bedwars-4x4}; fehlt sie, wird sie aus dem Servernamen abgeleitet. */
    public static final String SERVER_TEMPLATE = "NETWORK_SERVER_TEMPLATE";
    /**
     * Kategorie {@code lobby|game|persistent}. Annahme: Spec 08 nennt keine eigene Variable; ohne sie gilt die
     * Kategorie der Standard-Templates aus Spec 08 §6.5. Der Controller kann sie über {@code env} im Template setzen.
     */
    public static final String SERVER_CATEGORY = "NETWORK_SERVER_CATEGORY";
    /** Adresse, unter der der Proxy den Server erreicht (Standard: Hostname). */
    public static final String SERVER_HOST = "NETWORK_SERVER_HOST";
    /** Port des Servers (Standard 25565). */
    public static final String SERVER_PORT = "NETWORK_SERVER_PORT";
    /** Umgebung {@code dev|staging|prod} (Standard {@code dev}). */
    public static final String ENV = "NETWORK_ENV";
    /** JDBC-URL von PostgreSQL (Pflicht). */
    public static final String DB_URL = "NETWORK_DB_URL";
    /** Datenbanknutzer (Pflicht). */
    public static final String DB_USER = "NETWORK_DB_USER";
    /** Datenbankpasswort (Pflicht, wird nie geloggt). */
    public static final String DB_PASSWORD = "NETWORK_DB_PASSWORD";
    /** Größe des Verbindungspools (Paper Standard 5, Spec 08 §6.2). */
    public static final String DB_POOL_SIZE = "NETWORK_DB_POOL_SIZE";
    /** Valkey-URL, z. B. {@code redis://valkey:6379} (Pflicht). */
    public static final String VALKEY_URL = "NETWORK_VALKEY_URL";
    /** Schlüssel für IP-HMACs (Spec 08 §6.15 IP-Datenschutz); ohne ihn keine IP-Bans. */
    public static final String HMAC_SECRET = "NETWORK_HMAC_SECRET";
    /** Öffentliche Web-Adresse, z. B. {@code https://retromc.net} (Chatlog, Entbannung). */
    public static final String WEB_ORIGIN = "NETWORK_WEB_ORIGIN";

    /**
     * Darstellungs-Ära des Servers {@code modern|legacy} (Standard {@code modern}; {@code legacy} = Option
     * {@code legacyStyle}, Spec 00 E7, Spec 01/03 „Ära 18“). Auf Lobbys mit {@code legacy} läuft der Newsticker als
     * Bossbar {@code §e§l{TITLE}} statt in der Actionbar (Spec 02 §4.5, Spec 03 §4.4).
     */
    public static final String UI_ERA = "NETWORK_UI_ERA";

    /** Nick-Pool: Quell-UUIDs für Mojang-signierte Skins (Sessionserver), durch Komma oder Leerzeichen getrennt. */
    public static final String NICK_SKIN_UUIDS = "NETWORK_NICK_SKIN_UUIDS";
    /** Nick-Pool: erzeugte Namen bei der Mojang-Namens-API prüfen ({@code true|false}, Standard {@code true}). */
    public static final String NICK_CHECK_MOJANG = "NETWORK_NICK_CHECK_MOJANG";
    /** Nick-Pool: Mindestabstand der Mojang-Anfragen in ms (Standard 1100, Rate-Limit-Schonung). */
    public static final String NICK_MOJANG_INTERVAL_MS = "NETWORK_NICK_MOJANG_INTERVAL_MS";
    /** Nick-Pool: MineSkin-API-Schlüssel (optional, geheim; nie ins Repo, nie ins Log). */
    public static final String MINESKIN_KEY = "NETWORK_MINESKIN_KEY";
    /** Nick-Pool: feste MineSkin-Skin-IDs (optional; ohne sie die öffentliche Liste). */
    public static final String NICK_MINESKIN_IDS = "NETWORK_NICK_MINESKIN_IDS";
    /** Nick-Pool: zusätzliche gesperrte Wörter für Namen, durch Komma getrennt. */
    public static final String NICK_BLOCKED_WORDS = "NETWORK_NICK_BLOCKED_WORDS";

    private EnvKeys() {
    }
}
