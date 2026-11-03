package net.retromc.paper.core.chat;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Baut eine Chatzeile ohne Server (Spec 02 §6.1 Schritt 2–4, §9 #16): {@code [Kanalpräfix] [Level-Icon]
 * [Kontextpräfix] [Clan-Tag] Name §7: §fNachricht} über {@code chat.player}. Kein Rang-Präfix im Chat; Name in
 * Rangfarbe (MODERN mit Verlauf), im Spiel in Teamfarbe. Farbiges GG ab Supremium: {@code §7§k--§r {farbe}{TEXT}
 * §7§k--}.
 */
public final class ChatFormatter {

    /** Wörter für farbiges GG (ganze Nachricht, Groß-/Kleinschreibung egal); {@code gl} nur in der Wartezeit. */
    public static final Set<String> GG_WORDS = Set.of("gg", "gege", "ggwp", "wp", "nd", "gl");
    /** Standardfarbe des farbigen GG (Spieler-Einstellung, Spec 02 §6.1 Nr. 4). */
    public static final TextColor GG_COLOR = NamedTextColor.GOLD;
    /** Font der Level-Glyphen im Ressourcenpaket (Spec 02 §6.1: Glyph Annahme). */
    public static final Key LEVEL_FONT = Key.key(BuildInfo.BRAND_ID, "level");

    private ChatFormatter() {
    }

    /** Clan-Baustein. */
    public enum ClanPart {
        /** kein Clan-Baustein (Lobby, Option {@code chat.noClanDash} aus). */
        NONE,
        /** Spielserver ohne Clan: {@code §7[-] }. */
        DASH,
        /** {@code §7[§e{tag}§7] }. */
        TAG
    }

    /**
     * Bausteine einer Zeile.
     *
     * @param level globales Level für das Icon oder {@code null} (genickt, ohne Ressourcenpaket)
     * @param contextPrefix Kontextpräfix des Moduls oder {@code null}
     * @param clanTag Tag bei {@link ClanPart#TAG}
     * @param nameColor Teamfarbe oder {@code null} = Rangfarbe
     * @param waiting Wartezeit (für {@code gl})
     */
    public record Line(ChatChannel channel, @Nullable Integer level, @Nullable Component contextPrefix,
            ClanPart clan, @Nullable String clanTag, String name, RankDisplay rank, @Nullable TextColor nameColor,
            Component message, boolean waiting) {

        public Line {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(clan, "clan");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Text des farbigen GG ({@code GG}), falls die Nachricht eines der Wörter ist und der Rang es darf. */
    public static Optional<String> coloredGg(String message, boolean allowed, boolean waiting) {
        String word = message.strip().toLowerCase(Locale.ROOT);
        if (!allowed || !GG_WORDS.contains(word) || ("gl".equals(word) && !waiting)) {
            return Optional.empty();
        }
        return Optional.of(word.toUpperCase(Locale.ROOT));
    }

    /** Name in Team- oder Rangfarbe (Supremium+ als Verlauf, LEGACY einfarbig). */
    public static Component name(Line line) {
        TextColor team = line.nameColor();
        if (team != null) {
            return Component.text(line.name(), team);
        }
        return ColorText.name(line.name(), line.rank().color(), line.rank().gradient(), true);
    }

    /** Fertige Zeile in der Sprache des Empfängers. */
    public static Component format(I18n i18n, Locale locale, Line line, String plainMessage) {
        Component channel = switch (line.channel()) {
            case ALL -> i18n.render(locale, "chat.channel.all");
            case SPECTATOR -> i18n.render(locale, "chat.channel.spectator");
            default -> Component.empty();
        };
        Integer level = line.level();
        Component icon = level == null ? Component.empty()
                : Component.text(Integer.toString(level)).font(LEVEL_FONT).append(Component.space());
        Component context = line.contextPrefix() == null ? Component.empty() : line.contextPrefix();
        String tag = line.clanTag();
        Component clan = switch (line.clan()) {
            case TAG -> tag == null ? Component.empty()
                    : i18n.render(locale, "chat.clan-tag", Placeholder.unparsed("tag", tag));
            case DASH -> i18n.render(locale, "chat.no-clan");
            case NONE -> Component.empty();
        };
        Optional<String> gg = coloredGg(plainMessage, line.rank().coloredGg(), line.waiting());
        Component message = gg.isPresent()
                ? i18n.render(locale, "chat.gg", TagResolver.resolver("gg_color", Tag.styling(GG_COLOR)),
                        Placeholder.unparsed("text", gg.get()))
                : line.message();
        TextColor base = line.nameColor() != null ? line.nameColor() : ColorText.color(line.rank().color());
        return i18n.render(locale, "chat.player", Placeholder.component("channel", channel),
                Placeholder.component("icon", icon), Placeholder.component("ctx", context),
                Placeholder.component("clan", clan), TagResolver.resolver("name_color", Tag.styling(base)),
                Placeholder.component("name", name(line)), Placeholder.component("message", message));
    }
}
