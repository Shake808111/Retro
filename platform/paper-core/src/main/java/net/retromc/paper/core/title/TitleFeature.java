package net.retromc.paper.core.title;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.stats.ModeCatalog;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Standard-{@link TitleService} (Spec 02 §4.8): Texte aus dem Katalog in der Sprache des Spielers. */
public final class TitleFeature implements CoreFeature<PaperCore>, TitleService {

    private @Nullable PaperCore core;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Titles";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        registration = context.services().registerDefault(TitleService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        core = null;
    }

    private I18n i18n() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Titles nicht aktiv");
        }
        return context.services().get(I18n.class);
    }

    @Override
    public void show(Player player, String key, @Nullable String subKey, Title.Times times, TagResolver... args) {
        I18n i18n = i18n();
        Locale locale = i18n.locale(player.getUniqueId());
        player.showTitle(Title.title(i18n.render(locale, key, args), subKey == null ? Component.empty()
                : i18n.render(locale, subKey, args), times));
    }

    @Override
    public void win(Player player, Component winner, boolean match) {
        show(player, "title.win", match ? "title.win.sub.match" : "title.win.sub", WIN,
                Placeholder.component("name", winner), TagResolver.resolver("win_color", Tag.styling(style -> {
                })));
    }

    @Override
    public void intro(Player player, ModeKey mode, String map) {
        Locale locale = i18n().locale(player.getUniqueId());
        Optional<StatsMode> found = Optional.ofNullable(core).flatMap(context -> context.services()
                .find(ModeCatalog.class)).flatMap(catalog -> catalog.mode(mode));
        String color = found.map(StatsMode::color).orElse("white");
        String name = Messages.plain(locale, found.map(StatsMode::i18nKey).orElse("mode." + mode.key()), Map.of());
        show(player, "title.intro", "title.intro.sub", INTRO, ColorText.styling("mode_color", color),
                Placeholder.component("mode", ColorText.name(name, color, found.map(StatsMode::gradient)
                        .orElse(null), true)), Placeholder.unparsed("map", map));
    }

    @Override
    public void go(Player player) {
        show(player, "title.go", null, GO);
    }

    @Override
    public void respawn(Player player, int seconds) {
        show(player, seconds == 1 ? "title.respawn.one" : "title.respawn", null, RESPAWN,
                Placeholder.unparsed("n", Integer.toString(seconds)));
    }
}
