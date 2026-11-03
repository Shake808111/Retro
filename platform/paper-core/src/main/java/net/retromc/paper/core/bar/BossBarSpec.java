package net.retromc.paper.core.bar;

import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.i18n.Formats;
import net.retromc.platform.common.text.I18n;

/**
 * Inhalt einer Bossbar (Spec 02 §4.5): Text, Fortschritt 0–1, Farbe und Stil.
 */
public record BossBarSpec(Component title, float progress, BossBar.Color color, BossBar.Overlay overlay) {

    public BossBarSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(overlay, "overlay");
        if (progress < 0f || progress > 1f) {
            throw new IllegalArgumentException("Fortschritt 0–1: " + progress);
        }
    }

    /** Saison (Duels-Lobby): {@code §cSeason: §e{Mon}-{Mon} {yyyy}}, BLUE, PROGRESS, 1.0. */
    public static BossBarSpec season(I18n i18n, Locale locale, YearMonth start, YearMonth end) {
        return new BossBarSpec(i18n.render(locale, "bar.season", Placeholder.unparsed("range",
                Formats.of(locale).season(start, end))), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }

    /** Tutorial: {@code §6Tutorial §7({step}/{total}): §f{text} §7/tutorial}, GREEN, PROGRESS, step/total. */
    public static BossBarSpec tutorial(I18n i18n, Locale locale, int step, int total, Component text) {
        if (total < 1 || step < 0 || step > total) {
            throw new IllegalArgumentException("Schritt " + step + "/" + total);
        }
        return new BossBarSpec(i18n.render(locale, "bar.tutorial", Placeholder.unparsed("step",
                Integer.toString(step)), Placeholder.unparsed("total", Integer.toString(total)),
                Placeholder.component("text", text)), (float) step / total, BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS);
    }

    /** Newsticker {@code LEGACY}: {@code §e§l{TITLE}}, PINK, 1.0. */
    public static BossBarSpec news(I18n i18n, Locale locale, String title) {
        return new BossBarSpec(i18n.render(locale, "bar.news", Placeholder.unparsed("title",
                title.toUpperCase(Locale.ROOT))), 1f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
    }
}
