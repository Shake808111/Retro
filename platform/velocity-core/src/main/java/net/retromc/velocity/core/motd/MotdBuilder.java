package net.retromc.velocity.core.motd;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.platform.common.text.I18n;

/**
 * Inhalt der Serverliste (Spec 01 §4.1): Zeile 1 {@code motd.line1} zentriert auf 45 Zeichen, Zeile 2 News
 * ({@code motd.line2}: erstes Wort §9, Rest §d) bzw. {@code motd.line2.maintenance}, Versionstext bei Wartung
 * ({@code motd.version.maintenance}, Protokoll -1) oder zu altem Client.
 */
public final class MotdBuilder {

    /** Breite für die Zentrierung von Zeile 1. */
    public static final int WIDTH = 45;
    /** Protokoll im Wartungs-Versionstext: Client zeigt den Text rot statt der Spielerzahl. */
    public static final int MAINTENANCE_PROTOCOL = -1;

    private final I18n i18n;

    public MotdBuilder(I18n i18n) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
    }

    /** Version im Ping. */
    public record Version(int protocol, String name) {
    }

    /** Beschreibung aus beiden Zeilen. */
    public Component description(Locale locale, MotdSettings settings, boolean maintenance) {
        Component line2 = line2(locale, settings, maintenance);
        Component line1 = line1(locale);
        return line2.equals(Component.empty()) ? line1 : line1.append(Component.newline()).append(line2);
    }

    public Component line1(Locale locale) {
        Component line = i18n.render(locale, "motd.line1");
        int length = PlainTextComponentSerializer.plainText().serialize(line).length();
        int padding = Math.max(0, (WIDTH - length) / 2);
        return padding == 0 ? line : Component.text(" ".repeat(padding)).append(line);
    }

    public Component line2(Locale locale, MotdSettings settings, boolean maintenance) {
        if (maintenance) {
            return i18n.render(locale, "motd.line2.maintenance");
        }
        if (settings.news().isEmpty()) {
            return Component.empty();
        }
        String news = settings.news();
        int space = news.indexOf(' ');
        String word1 = space < 0 ? news : news.substring(0, space);
        String rest = space < 0 ? "" : news.substring(space + 1);
        return i18n.render(locale, "motd.line2", Placeholder.unparsed("word1", word1),
                Placeholder.unparsed("rest", rest));
    }

    /**
     * Versionsangabe: bei Wartung {@code motd.version.maintenance} mit Protokoll -1, bei zu altem Client der
     * Versionstext mit dem kleinsten unterstützten Protokoll, sonst {@code null} (Proxy-Standard).
     */
    public Version version(Locale locale, MotdSettings settings, boolean maintenance, int clientProtocol,
            int proxyProtocol, String proxyName) {
        if (maintenance) {
            return new Version(MAINTENANCE_PROTOCOL, legacy(i18n.render(locale, "motd.version.maintenance")));
        }
        if (settings.minProtocol() > 0 && clientProtocol < settings.minProtocol()) {
            return new Version(settings.minProtocol(), settings.versionText());
        }
        return new Version(proxyProtocol, proxyName);
    }

    private static String legacy(Component component) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(component);
    }
}
