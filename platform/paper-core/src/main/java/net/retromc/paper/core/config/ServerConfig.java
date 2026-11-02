package net.retromc.paper.core.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.platform.common.config.ConfigException;
import net.retromc.platform.common.config.EnvKeys;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.config.NodeConfig;

/**
 * Konfiguration eines Paper-Servers (Spec 08 §6.2 Nr. 1, §6.6, §6.15): gemeinsame Werte ({@link NodeConfig}, Pool
 * Standard 5) plus Template, Kategorie, Adresse und Port.
 *
 * <ul>
 *   <li>Template: {@code NETWORK_SERVER_TEMPLATE}, sonst der Servername ohne laufende Nummer
 *       ({@code bedwars-1} → {@code bedwars}, {@code lobby} → {@code lobby}; statische Compose-Server).</li>
 *   <li>Kategorie: {@code NETWORK_SERVER_CATEGORY}, sonst nach den Standard-Templates aus Spec 08 §6.5:
 *       {@code lobby}, {@code premiumlobby}, {@code silentlobby}, {@code lobby-*} → LOBBY; {@code cb-*},
 *       {@code smp-*} → PERSISTENT; alles andere GAME.</li>
 * </ul>
 *
 * @param node gemeinsame Werte
 * @param template Template-ID
 * @param category Kategorie
 * @param host Adresse für den Proxy
 * @param port Port
 */
public record ServerConfig(NodeConfig node, TemplateId template, ServerCategory category, String host, int port) {

    /** Poolgröße auf Paper, wenn {@code NETWORK_DB_POOL_SIZE} fehlt (Spec 08 §6.2 Nr. 2). */
    public static final int DEFAULT_POOL_SIZE = 5;
    /** Standard-Port. */
    public static final int DEFAULT_PORT = 25565;

    private static final Pattern NUMBERED = Pattern.compile("(.+)-[1-9][0-9]*");

    public ServerConfig {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("Adresse darf nicht leer sein");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Ungültiger Port: " + port);
        }
    }

    /**
     * Liest alle Variablen und meldet alle Probleme zusammen.
     *
     * @param hostname Rechnername, falls {@code NETWORK_SERVER_HOST} fehlt
     * @throws ConfigException bei fehlenden oder ungültigen Werten
     */
    public static ServerConfig read(EnvReader env, Supplier<String> hostname) {
        Optional<String> template = env.optional(EnvKeys.SERVER_TEMPLATE, TemplateId.PATTERN,
                "a-z, 0-9 und '-', 2–24 Zeichen");
        Optional<ServerCategory> category = env.choice(EnvKeys.SERVER_CATEGORY, ServerCategory.class);
        Optional<String> host = env.optional(EnvKeys.SERVER_HOST);
        int port = env.integer(EnvKeys.SERVER_PORT, DEFAULT_PORT, 1, 65_535);
        NodeConfig node = NodeConfig.read(env, DEFAULT_POOL_SIZE);
        TemplateId templateId;
        try {
            templateId = template.map(TemplateId::of).orElseGet(() -> templateOf(node.name()));
        } catch (IllegalArgumentException e) {
            throw new ConfigException(List.of(e.getMessage()));
        }
        return new ServerConfig(node, templateId, category.orElseGet(() -> categoryOf(templateId)),
                host.orElseGet(hostname), port);
    }

    /** Template aus dem Servernamen ({@code bedwars-4x4-12} → {@code bedwars-4x4}, Spec 08 §6.6). */
    public static TemplateId templateOf(String serverName) {
        Matcher matcher = NUMBERED.matcher(serverName);
        String candidate = matcher.matches() ? matcher.group(1) : serverName;
        if (!TemplateId.PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Aus '" + serverName + "' folgt kein Template; "
                    + EnvKeys.SERVER_TEMPLATE + " setzen");
        }
        return TemplateId.of(candidate);
    }

    /** Kategorie der Standard-Templates (Spec 08 §6.5). */
    public static ServerCategory categoryOf(TemplateId template) {
        String id = template.value();
        if (template.equals(TemplateId.LOBBY) || template.equals(TemplateId.PREMIUM_LOBBY)
                || template.equals(TemplateId.SILENT_LOBBY) || id.startsWith("lobby-")) {
            return ServerCategory.LOBBY;
        }
        if (id.startsWith("cb-") || id.startsWith("smp-")) {
            return ServerCategory.PERSISTENT;
        }
        return ServerCategory.GAME;
    }

    /** Servername ({@code src} im Envelope). */
    public String name() {
        return node.name();
    }
}
