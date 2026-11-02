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

    private EnvKeys() {
    }
}
