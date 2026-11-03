package net.retromc.paper.core.title;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.retromc.core.api.stats.ModeKey;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Titles (Spec 02 §4.8, §8.1 {@code TitleService}) in der Sprache des Spielers. Zeiten in Ticks (ein/bleiben/aus).
 */
public interface TitleService {

    /** Sieger 10/70/20 (Annahme). */
    Title.Times WIN = ticks(10, 70, 20);
    /** Modus-Intro 10/50/10 (Annahme). */
    Title.Times INTRO = ticks(10, 50, 10);
    /** Start {@code LOS!} 0/20/10. */
    Title.Times GO = ticks(0, 20, 10);
    /** Respawn-Countdown 0/25/0, jede Sekunde neu. */
    Title.Times RESPAWN = ticks(0, 25, 0);
    /** Rolle (TTT) und CityBuild-Join 10/70/20. */
    Title.Times ROLE = ticks(10, 70, 20);

    /**
     * Zeigt {@code key} (und {@code subKey}) mit {@code args}.
     *
     * @param subKey Untertitel oder {@code null}
     */
    void show(Player player, String key, @Nullable String subKey, Title.Times times, TagResolver... args);

    /** Sieger {@code {rankOrTeamColor}{name}} / {@code §fhat die Runde gewonnen} (Duels: {@code das Match}). */
    void win(Player player, Component winner, boolean match);

    /** Modus-Intro {@code {modeColor}{modeName}} / {@code §f{map}}. */
    void intro(Player player, ModeKey mode, String map);

    /** {@code §aLOS!}. */
    void go(Player player);

    /** {@code §cRespawn in §e{n} §cSekunden} (1 → {@code Sekunde}). */
    void respawn(Player player, int seconds);

    static Title.Times ticks(int in, int stay, int out) {
        return Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(out * 50L));
    }
}
