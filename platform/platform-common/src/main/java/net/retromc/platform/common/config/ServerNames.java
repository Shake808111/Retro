package net.retromc.platform.common.config;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;

/**
 * Template, Kategorie und Modus aus dem Servernamen (Spec 08 §6.5/§6.6), wenn keine Server-Registry verfügbar ist:
 * {@code bedwars-4x4-12} → Template {@code bedwars-4x4}, Kategorie GAME, Modus {@code bedwars}.
 */
public final class ServerNames {

    private static final Pattern NUMBERED = Pattern.compile("(.+)-[1-9][0-9]*");

    private ServerNames() {
    }

    /** Template aus dem Servernamen; leer, wenn daraus keine gültige Template-ID folgt. */
    public static Optional<TemplateId> templateOf(String serverName) {
        Matcher matcher = NUMBERED.matcher(serverName);
        String candidate = matcher.matches() ? matcher.group(1) : serverName;
        return TemplateId.PATTERN.matcher(candidate).matches() ? Optional.of(TemplateId.of(candidate))
                : Optional.empty();
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

    /** Kategorie eines Servers nach Namen; unbekannte Namen gelten als Lobby (kein Nick, kein Rundenbezug). */
    public static ServerCategory categoryOfServer(String serverName) {
        return templateOf(serverName).map(ServerNames::categoryOf).orElse(ServerCategory.LOBBY);
    }

    /** Modus eines Servers, z. B. {@code ttt-2} → {@code ttt} (Report-Gründe, Spec 01 §6.9). */
    public static String modeOfServer(String serverName) {
        String template = templateOf(serverName).map(TemplateId::value).orElse(serverName);
        int dash = template.indexOf('-');
        return (dash < 0 ? template : template.substring(0, dash)).toLowerCase(Locale.ROOT);
    }
}
