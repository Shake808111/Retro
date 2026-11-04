package net.retromc.game.framework.kill;

import java.util.ArrayList;
import java.util.List;
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

    /** Schritt einer Kill-Meldung in Sendereihenfolge. */
    public enum Step {
        /** {@code common.kit_reveal}: Kit des Killers an das Opfer (Spec 03 §6.9). */
        KILLER_KIT,
        /** {@code common.kill.kit}: Kit des Opfers an alle (SkyWars {@code sw.kill.kit}, Spec 04 §6.2.5 Nr. 4). */
        VICTIM_KIT,
        /** {@code common.kill} an alle (Opfer ggf. {@code common.killed_by}). */
        KILL,
        /** {@code common.death} an alle (ohne Killer). */
        DEATH,
        /** {@code common.killed_by} nur an das Opfer (TTT-Stil). */
        KILLED_BY,
        /** {@code common.kill.self} an den Killer. */
        KILL_SELF,
        /** {@code common.killer_health} an das Opfer. */
        KILLER_HEALTH,
        /** {@code common.remaining} an alle. */
        REMAINING
    }

    /**
     * Reihenfolge der Meldungen (Spec 04 §9 #13: Kit → getötet → Leben des Killers (Opfer) → verbleibende Spieler).
     *
     * @param killer           es gibt einen Killer
     * @param showKillerHealth Modus zeigt dem Opfer die Leben des Killers
     */
    public static List<Step> plan(KillOptions options, boolean killer, boolean showKillerHealth) {
        List<Step> steps = new ArrayList<>();
        if (killer && options.kit() != null) {
            steps.add(Step.KILLER_KIT);
        }
        if (killer && options.victimKit() != null) {
            steps.add(Step.VICTIM_KIT);
        }
        if (options.toVictimOnly()) {
            if (killer) {
                steps.add(Step.KILLED_BY);
            }
        } else {
            steps.add(killer ? Step.KILL : Step.DEATH);
        }
        if (killer && (options.points() > 0 || (options.xp() > 0 && !options.xpInBroadcast()))) {
            steps.add(Step.KILL_SELF);
        }
        if (killer && showKillerHealth) {
            steps.add(Step.KILLER_HEALTH);
        }
        if (options.remaining() >= 0) {
            steps.add(Step.REMAINING);
        }
        return List.copyOf(steps);
    }

    public void kill(Player victim, @Nullable Player killer, KillOptions options) {
        GameTexts texts = game.texts();
        TagResolver victimName = texts.name("victim", victim);
        for (Step step : plan(options, killer != null, game.definition().showKillerHealth())) {
            switch (step) {
                case KILLER_KIT -> texts.send(victim, "common.kit_reveal", texts.name("killer",
                        Objects.requireNonNull(killer)), Placeholder.unparsed("kit",
                                String.valueOf(options.kit())));
                case VICTIM_KIT -> texts.broadcast("common.kill.kit", victimName, Placeholder.unparsed("kit",
                        String.valueOf(options.victimKit())));
                case KILLED_BY -> texts.send(victim, "common.killed_by", texts.name("killer",
                        Objects.requireNonNull(killer)));
                case DEATH -> texts.broadcast("common.death", victimName);
                case KILL -> broadcastKill(texts, victim, Objects.requireNonNull(killer), victimName,
                        options);
                case KILL_SELF -> texts.send(Objects.requireNonNull(killer), "common.kill.self", victimName,
                        Placeholder.unparsed("points", Integer.toString(options.points())),
                        Placeholder.unparsed("xp", Integer.toString(options.xp())));
                case KILLER_HEALTH -> {
                    Player source = Objects.requireNonNull(killer);
                    texts.send(victim, "common.killer_health", texts.name("killer", source),
                            Placeholder.component("hearts", HeartLine.component(source.getHealth())));
                }
                case REMAINING -> texts.broadcast(texts.count("common.remaining", options.remaining()),
                        Placeholder.unparsed("n", Integer.toString(options.remaining())));
                default -> throw new IllegalStateException(step.name());
            }
        }
        Bukkit.getPluginManager().callEvent(new GamePlayerKillEvent(victim, killer, game.definition().modeId()));
    }

    private void broadcastKill(GameTexts texts, Player victim, Player killer, TagResolver victimName,
            KillOptions options) {
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (receiver.equals(victim) && options.victimMessage()) {
                texts.send(victim, "common.killed_by", texts.name("killer", killer));
                continue;
            }
            Locale locale = texts.locale(receiver);
            boolean showXp = options.xpInBroadcast() && options.xp() > 0
                    && (!options.xpKillerOnly() || receiver.equals(killer));
            Component xp = showXp ? texts.i18n().render(locale, texts.key("common.kill.xp"),
                    Placeholder.unparsed("xp", Integer.toString(options.xp()))) : Component.empty();
            texts.send(receiver, "common.kill", victimName, texts.name("killer", killer),
                    Placeholder.component("xp", xp));
        }
    }
}
