package net.retromc.paper.core.bar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;

/**
 * Eintrag des Lobby-Newstickers ({@code ui:news}, LIST mit JSON {@code {color,title,url}}, Spec 02 §2.2, §4.6).
 *
 * @param color Farbe des Titels (§-Code, MiniMessage-Name oder {@code #RRGGBB})
 */
public record NewsItem(String color, String title, String url) {

    /** Takt des Tickers in Ticks (8 s, Spec 02 O22). */
    public static final int ROTATE_TICKS = 160;

    public NewsItem {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(url, "url");
    }

    /** Liest einen Listeneintrag; ungültiges JSON wird übersprungen. */
    public static Optional<NewsItem> parse(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return Optional.of(new NewsItem(object.get("color").getAsString(), object.get("title").getAsString(),
                    object.get("url").getAsString()));
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    public static List<NewsItem> parseAll(List<String> json) {
        List<NewsItem> items = new ArrayList<>();
        json.forEach(entry -> parse(entry).ifPresent(items::add));
        return List.copyOf(items);
    }

    /** {@code §e+ {color}§l{TITLE}: §f{url}}. */
    public Component render(I18n i18n, Locale locale) {
        return i18n.render(locale, "actionbar.news", ColorText.styling("news_color", color),
                Placeholder.unparsed("title", title.toUpperCase(Locale.ROOT)), Placeholder.unparsed("url", url));
    }
}
