package net.retromc.paper.core.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.jspecify.annotations.Nullable;

/**
 * Texte der Tablist ohne Server (Spec 02 §4.4): Header, Footer und Einträge. paper-core ist einziger Renderer von
 * Header und Footer (kein Proxy-Header).
 */
public final class TabFormat {

    /** Font des Bild-Banners im Ressourcenpaket (Header Zeile 0). */
    public static final Key BANNER_FONT = Key.key(BuildInfo.BRAND_ID, "banner");
    /** Glyphe des Banners. */
    public static final String BANNER_GLYPH = "";

    private TabFormat() {
    }

    /**
     * Header: Zeile 0 Banner (nur mit Ressourcenpaket, sonst entfällt sie), Zeile 1 {@code tab.header.mode}, Zeile 2
     * {@code tab.header.report}.
     */
    public static Component header(I18n i18n, Locale locale, ServerIdentity server, boolean banner) {
        List<Component> lines = new ArrayList<>();
        if (banner) {
            lines.add(Component.text(BANNER_GLYPH).font(BANNER_FONT));
        }
        lines.add(i18n.render(locale, "tab.header.mode", ColorText.styling("mode_color", server.color()),
                Placeholder.unparsed("server_display", server.display())));
        lines.add(i18n.render(locale, "tab.header.report"));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /**
     * Footer: Standard {@code tab.footer.1}/{@code .2}; mit Server-Override {@code tab.footer.server} und die
     * Zusatzzeilen des Moduls.
     *
     * @param serverLines Zusatzzeilen des Overrides oder {@code null} = Standard-Footer
     */
    public static Component footer(I18n i18n, Locale locale, @Nullable List<Component> serverLines, String server) {
        List<Component> lines = new ArrayList<>();
        if (serverLines == null) {
            lines.add(i18n.render(locale, "tab.footer.1"));
            lines.add(i18n.render(locale, "tab.footer.2"));
        } else {
            lines.add(i18n.render(locale, "tab.footer.server", Placeholder.unparsed("server", server)));
            lines.addAll(serverLines);
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Lobby-Eintrag {@code [{rankColor}{prefix} §7| ]{rankColor}{name}[ §7[§e{tag}§7]]}. */
    public static Component lobbyEntry(I18n i18n, Locale locale, RankDisplay rank, String name,
            @Nullable String clanTag) {
        Component prefix = rankPrefix(i18n, locale, rank);
        Component colored = ColorText.name(name, rank.color(), rank.gradient(), true);
        return Component.textOfChildren(prefix, colored, clanSuffix(i18n, locale, clanTag));
    }

    /** Spiel-Eintrag {@code {teamColor}{teamName} §7| §f{name}[ §7[§e{tag}§7]]}. */
    public static Component teamEntry(I18n i18n, Locale locale, TextColor teamColor, String team, String name,
            @Nullable String clanTag) {
        Component entry = i18n.render(locale, "tab.entry.team", TagResolver.resolver("team_color",
                Tag.styling(teamColor)), Placeholder.unparsed("team", team), Placeholder.unparsed("name", name));
        return Component.textOfChildren(entry, clanSuffix(i18n, locale, clanTag));
    }

    /** {@code {rankColor}{prefix} §7| } oder leer, wenn der Rang kein Präfix hat (Premium, Spieler). */
    public static Component rankPrefix(I18n i18n, Locale locale, RankDisplay rank) {
        String prefix = rank.prefix();
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        return i18n.render(locale, "tab.entry.rank", ColorText.styling("rank_color", rank.color()),
                Placeholder.unparsed("prefix", prefix));
    }

    /** {@code  §7[§e{tag}§7]} oder leer. */
    public static Component clanSuffix(I18n i18n, Locale locale, @Nullable String clanTag) {
        return clanTag == null ? Component.empty()
                : i18n.render(locale, "tab.entry.clan", Placeholder.unparsed("tag", clanTag));
    }
}
