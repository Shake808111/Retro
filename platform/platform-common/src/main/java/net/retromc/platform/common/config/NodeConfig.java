package net.retromc.platform.common.config;

import java.util.Objects;
import java.util.regex.Pattern;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.data.DatabaseConfig;

/**
 * Gemeinsame Konfiguration jedes Netzwerk-Knotens (Paper-Server oder Proxy) aus den Umgebungsvariablen
 * (Spec 08 §6.2 Nr. 1, §6.15). Pflicht sind {@code NETWORK_SERVER_NAME}, {@code NETWORK_DB_*} und
 * {@code NETWORK_VALKEY_URL}. Datenbank-Migrationen gehören nicht hierher: sie laufen nur über den Compose-Dienst
 * {@code migrate} bzw. {@code controller migrate} (Spec 08 §3 C4).
 *
 * @param name Servername bzw. Proxy-ID, gleichzeitig {@code src} im Envelope
 * @param stage Betriebsumgebung
 * @param database Verbindungsdaten PostgreSQL
 * @param valkeyUrl Valkey-URL ({@code redis://} oder {@code rediss://})
 */
public record NodeConfig(String name, NetworkStage stage, DatabaseConfig database, String valkeyUrl) {

    /** Valkey-URLs, die Lettuce versteht. */
    public static final Pattern VALKEY_URL = Pattern.compile("rediss?://\\S+");

    private static final Pattern JDBC_URL = Pattern.compile("jdbc:postgresql://\\S+");

    public NodeConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(valkeyUrl, "valkeyUrl");
        if (!TemplateId.SERVER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Ungültiger Servername: '" + name + "'");
        }
        if (!VALKEY_URL.matcher(valkeyUrl).matches()) {
            throw new IllegalArgumentException("Ungültige Valkey-URL");
        }
    }

    /**
     * Liest die gemeinsamen Variablen, prüft alle bis dahin vermerkten Probleme ({@link EnvReader#check()}) und baut
     * die Konfiguration. Plattform-eigene Variablen deshalb vorher lesen, damit alle Probleme zusammen gemeldet werden.
     *
     * @param defaultPoolSize Poolgröße, wenn {@code NETWORK_DB_POOL_SIZE} fehlt
     * @throws ConfigException bei fehlenden oder ungültigen Werten
     */
    public static NodeConfig read(EnvReader env, int defaultPoolSize) {
        String name = env.require(EnvKeys.SERVER_NAME, TemplateId.SERVER_NAME, "nur a-z, 0-9 und '-', höchstens 48");
        NetworkStage stage = env.choice(EnvKeys.ENV, NetworkStage.class).orElse(NetworkStage.DEV);
        String url = env.require(EnvKeys.DB_URL, JDBC_URL, "jdbc:postgresql://host:port/datenbank");
        String user = env.require(EnvKeys.DB_USER);
        String password = env.require(EnvKeys.DB_PASSWORD);
        int poolSize = env.integer(EnvKeys.DB_POOL_SIZE, defaultPoolSize, 1, 100);
        String valkeyUrl = env.require(EnvKeys.VALKEY_URL, VALKEY_URL, "redis://host:port");
        env.check();
        return new NodeConfig(name, stage, new DatabaseConfig(url, user, password, poolSize), valkeyUrl);
    }
}
