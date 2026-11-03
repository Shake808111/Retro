package net.retromc.game.framework.kill;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.event.GamePlayerKillEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Kill- und Tod-Meldungen (Spec 03 §6.9, Baukasten {@code KillFeed}). Reihenfolge wie SkyWars-Beleg (Spec 04 §9
 * #13): Kit des Killers (Opfer) → Rundsendung → Leben des Killers (Opfer) → verbleibende Spieler. Namen in Team- bzw.
 * Rangfarbe, Rest {@code §7}.
 */
public final class KillFeed {

    private final Game game;

    public KillFeed(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    public void kill(Player victim, @Nullable Player killer, KillOptions options) {
        GameTexts texts = game.texts();
        TagResolver victimName = texts.name("victim", victim);
        if (killer != null && options.kit() != null) {
            texts.send(victim, "common.kit_reveal", texts.name("killer", killer),
                    Placeholder.unparsed("kit", options.kit()));
        }
        if (options.toVictimOnly()) {
            if (killer != null) {
                texts.send(victim, "common.killed_by", texts.name("killer", killer));
            }
        } else {
            for (Player receiver : Bukkit.getOnlinePlayers()) {
                if (receiver.equals(victim) && killer != null && options.victimMessage()) {
                    texts.send(victim, "common.killed_by", texts.name("killer", killer));
                    continue;
                }
                Locale locale = texts.locale(receiver);
                if (killer == null) {
                    texts.send(receiver, "common.death", victimName);
                } else {
                    Component xp = options.xpInBroadcast() && options.xp() > 0
                            ? texts.i18n().render(locale, "common.kill.xp", Placeholder.unparsed("xp",
                                    Integer.toString(options.xp()))) : Component.empty();
                    texts.send(receiver, "common.kill", victimName, texts.name("killer", killer),
                            Placeholder.component("xp", xp));
                }
            }
        }
        if (killer != null && (options.points() > 0 || (options.xp() > 0 && !options.xpInBroadcast()))) {
            texts.send(killer, "common.kill.self", victimName,
                    Placeholder.unparsed("points", Integer.toString(options.points())),
                    Placeholder.unparsed("xp", Integer.toString(options.xp())));
        }
        if (killer != null && game.definition().showKillerHealth()) {
            texts.send(victim, "common.killer_health", texts.name("killer", killer),
                    Placeholder.component("hearts", HeartLine.component(killer.getHealth())));
        }
        if (options.remaining() >= 0) {
            texts.broadcast(texts.count("common.remaining", options.remaining()),
                    Placeholder.unparsed("n", Integer.toString(options.remaining())));
        }
        Bukkit.getPluginManager().callEvent(new GamePlayerKillEvent(victim, killer, game.definition().modeId()));
    }
}
