package net.retromc.skywars.challenge;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.end.GameResult;
import net.retromc.paper.core.bar.BossBarSpec;
import net.retromc.paper.core.bar.BossbarService;
import net.retromc.skywars.SkyWarsMode;
import net.retromc.skywars.SwPlayer;
import net.retromc.skywars.event.ChallengeResultEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Runden-Challenge (Spec 04 §6.2.6, §4.8): eine zufällige je Runde für alle; Bossbar {@code §6Challenge: §7{text}}
 * (gelb, Fortschritt = Zählziel bzw. verbleibender Anteil der Rundenzeit), Scheitern sofort
 * ({@code sw.challenge.failed}), Erfolg am Rundenende ({@code sw.challenge.done} mit XP).
 */
public final class ChallengeService {

    private final SkyWarsMode mode;
    private final ChallengeSet set;
    private final Random random;
    private @Nullable Challenge current;

    public ChallengeService(SkyWarsMode mode, ChallengeSet set, Random random) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.set = Objects.requireNonNull(set, "set");
        this.random = Objects.requireNonNull(random, "random");
    }

    public @Nullable Challenge current() {
        return current;
    }

    /** Rundenstart: Challenge wählen, Zähler je Teilnehmer anlegen, Bossbar zeigen. */
    public void start() {
        current = set.pick(random);
        Challenge challenge = current;
        if (challenge == null) {
            return;
        }
        for (Player player : mode.game().alivePlayers()) {
            mode.sw(player.getUniqueId()).challenge(new ChallengeTracker(challenge));
        }
        update();
    }

    /** 1 Hz: Bossbars aktualisieren. */
    public void update() {
        Challenge challenge = current;
        if (challenge == null) {
            return;
        }
        float timeShare = (float) Math.clamp(1.0 - mode.second() / (double) Math.max(1, mode.maxSeconds()), 0.0,
                1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChallengeTracker tracker = mode.sw(player.getUniqueId()).challenge();
            if (tracker == null) {
                continue;
            }
            float share = tracker.share();
            Locale locale = mode.texts().locale(player);
            Component title = mode.texts().i18n().render(locale, "sw.bossbar.challenge", Placeholder.component("text",
                    mode.texts().i18n().render(locale, challenge.textKey())));
            mode.game().services().find(BossbarService.class).ifPresent(bars -> bars.show(player,
                    BossbarService.GAME, new BossBarSpec(title, share >= 0 ? share : timeShare, BossBar.Color.YELLOW,
                            BossBar.Overlay.PROGRESS)));
        }
    }

    /** Meldet einen Wechsel (Scheitern sofort, Fortschritt in der Bossbar). */
    public void report(Player player, ChallengeTracker.Change change) {
        if (change == ChallengeTracker.Change.FAILED) {
            mode.texts().send(player, "sw.challenge.failed");
            ChallengeTracker tracker = mode.sw(player.getUniqueId()).challenge();
            if (tracker != null) {
                Bukkit.getPluginManager().callEvent(new ChallengeResultEvent(player, tracker.challenge(), false));
            }
        }
        if (change != ChallengeTracker.Change.NONE) {
            update();
        }
    }

    /** Rundenende: Erfolg melden und XP gutschreiben; Bossbars weg. */
    public void finish(GameResult result) {
        Challenge challenge = current;
        for (Player player : Bukkit.getOnlinePlayers()) {
            mode.game().services().find(BossbarService.class).ifPresent(bars -> bars.hide(player,
                    BossbarService.GAME));
        }
        if (challenge == null || result.aborted()) {
            return;
        }
        int xp = set.xpOf(challenge);
        for (GamePlayer gamePlayer : mode.game().players()) {
            SwPlayer sw = mode.sw(gamePlayer.uuid());
            ChallengeTracker tracker = sw.challenge();
            if (!gamePlayer.participant() || tracker == null || !tracker.success(result.won(gamePlayer))) {
                continue;
            }
            gamePlayer.addXp(xp);
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                mode.texts().send(player, "sw.challenge.done", Placeholder.unparsed("xp", Integer.toString(xp)));
                Bukkit.getPluginManager().callEvent(new ChallengeResultEvent(player, challenge, true));
            }
        }
    }

    /** Challenge des Spielers geschafft (für den Rundenblock; Rundenende vorausgesetzt). */
    public boolean done(GamePlayer player, GameResult result) {
        ChallengeTracker tracker = mode.sw(player.uuid()).challenge();
        return tracker != null && tracker.success(result.won(player));
    }
}
