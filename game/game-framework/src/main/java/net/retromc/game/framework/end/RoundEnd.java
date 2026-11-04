package net.retromc.game.framework.end;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.PlayerMatchStats;
import net.retromc.core.api.stats.SubmitResult;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.core.messaging.payload.GameEnded;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.event.GameEndEvent;
import net.retromc.game.framework.phase.PhaseTimer;
import net.retromc.game.framework.phase.RestartPlan;
import net.retromc.game.framework.spectate.SpectatorReason;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.paper.core.coins.CoinService;
import net.retromc.paper.core.server.PlayerTransfer;
import net.retromc.paper.core.server.ServerLifecycle;
import net.retromc.paper.core.stats.StatsService;
import net.retromc.paper.core.title.TitleService;
import net.retromc.platform.common.text.ColorText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * Rundenende (Spec 03 §6.10, §8.1 {@code RoundEnd}): ENDING, Sieg-Meldung und Title, Feuerwerk, Statistik genau
 * einmal je {@code matchId}, dann je Spieler Rundenblock → {@code common.restart} (20) → Coins, Restart-Ansagen
 * 20/15/10/5…1 mit XP-Balken, bei 0 {@code SEND_LOBBY} mit Modus und {@code GAME_ENDED}, 3 s später Server-Stopp.
 */
public final class RoundEnd {

    /** Höchstwartezeit auf die Statistik-Buchung, bevor der Ablauf trotzdem weitergeht. */
    static final long SUBMIT_TIMEOUT_SECONDS = 5;
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final Game game;
    private final SubmitOnce<SubmitResult> submissions = new SubmitOnce<>();
    private final PhaseTimer timer;
    private volatile boolean finished;

    public RoundEnd(Game game) {
        this.game = Objects.requireNonNull(game, "game");
        this.timer = new PhaseTimer(game.plugin());
    }

    public boolean finished() {
        return finished;
    }

    /** Beendet die Runde; weitere Aufrufe haben keine Wirkung (Spec 03 §9 #16). */
    public void finish(GameResult result) {
        if (finished || game.state() != GameState.INGAME) {
            return;
        }
        finished = true;
        game.mode().onFinish(result);
        Instant endedAt = Instant.now();
        Map<UUID, Boolean> survived = new LinkedHashMap<>();
        game.players().forEach(player -> survived.put(player.uuid(), player.alive()));
        game.lifecycle().transitionTo(GameState.ENDING);
        for (Player player : game.alivePlayers()) {
            game.spectators().makeSpectator(player, SpectatorReason.ROUND_END, false);
        }
        Bukkit.getPluginManager().callEvent(new GameEndEvent(game.matchId(), result));
        if (result.aborted()) {
            restart(RestartPlan.ABORT, result, endedAt);
            return;
        }
        announceWinner(result);
        CompletableFuture<SubmitResult> submitted = game.privateRound().current().isPresent()
                ? CompletableFuture.completedFuture(null)
                : submissions.run(game.matchId(), () -> submit(result, survived, endedAt));
        CompletableFuture<SubmitResult> bounded = submitted.completeOnTimeout(null, SUBMIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS).exceptionally(error -> {
                    game.log().error("Statistik der Runde {} nicht gebucht", game.matchId(), error);
                    return null;
                });
        game.mainThread().accept(bounded, "Rundenende", submit -> {
            game.mode().onEnd(result, submit != null && submit.applied());
            blocks(result, survived);
            restart(RestartPlan.STANDARD, result, endedAt);
            payout(result);
        });
    }

    /** {@code StatsService.submit} genau einmal je Runde (auch bei doppeltem Aufruf). */
    public CompletableFuture<SubmitResult> submitOnce(UUID matchId, java.util.function.Supplier<
            CompletableFuture<SubmitResult>> action) {
        return submissions.run(matchId, action);
    }

    private void announceWinner(GameResult result) {
        GameTexts texts = game.texts();
        GameTeam team = result.winnerTeam();
        if (result.draw()) {
            texts.broadcast("common.draw");
            return;
        }
        List<Player> winners = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Locale locale = texts.locale(player);
            if (team != null) {
                texts.send(player, "common.win.team", texts.team(locale, team));
                showTitle(player, locale, texts.i18n().render(locale, "game.team.label", texts.team(locale, team)),
                        texts.team(locale, team));
            } else if (result.winner() != null) {
                UUID winner = result.winner();
                String name = texts.displayName(winner, Bukkit.getOfflinePlayer(winner).getName() == null
                        ? winner.toString() : Bukkit.getOfflinePlayer(winner).getName());
                texts.send(player, "common.win.player", Placeholder.unparsed("name", name),
                        Placeholder.unparsed("mode", game.definition().displayName()));
                showTitle(player, locale, ColorText.colored(texts.rankColor(winner), name),
                        Placeholder.unparsed("name", name));
            }
        }
        for (GamePlayer gamePlayer : game.players()) {
            if (result.won(gamePlayer)) {
                Player online = Bukkit.getPlayer(gamePlayer.uuid());
                if (online != null) {
                    winners.add(online);
                }
            }
        }
        fireworks(winners, team == null ? "§e" : team.colorCode());
    }

    /** Sieger-Title (Spec 02 §4.8) bzw. Modus-Override (BedWars {@code bw.title.win}, Spec 04 §4.7). */
    private void showTitle(Player player, Locale locale, Component winner, TagResolver overrideArgs) {
        game.services().find(TitleService.class).ifPresent(titles -> {
            String key = game.texts().key("title.win");
            if (key.equals("title.win")) {
                titles.win(player, winner, false);
            } else {
                titles.show(player, key, game.texts().key("title.win.sub"), TitleService.WIN, overrideArgs);
            }
        });
    }

    /** Feuerwerk 5 s am Sieger in Teamfarbe (Spec 03 §6.10 Nr. 2, Annahme). */
    private void fireworks(List<Player> winners, String color) {
        Color bukkit = Color.fromRGB(ColorText.color(color).value());
        for (int second = 0; second < 5; second++) {
            Bukkit.getScheduler().runTaskLater(game.plugin(), () -> winners.stream().filter(Player::isOnline)
                    .forEach(player -> player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
                        FireworkMeta meta = firework.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder().withColor(bukkit).with(FireworkEffect.Type.BALL)
                                .flicker(true).build());
                        meta.setPower(1);
                        firework.setFireworkMeta(meta);
                    })), second * 20L);
        }
    }

    private CompletableFuture<SubmitResult> submit(GameResult result, Map<UUID, Boolean> survived,
            Instant endedAt) {
        List<PlayerMatchStats> stats = new ArrayList<>();
        for (GamePlayer player : game.players()) {
            if (!player.participant()) {
                continue;
            }
            GameTeam team = player.team();
            stats.add(new PlayerMatchStats(player.uuid(), team == null ? null : team.index(),
                    result.placeOf(player.uuid()).orElse(null), player.kills(), player.deaths(), result.won(player),
                    survived.getOrDefault(player.uuid(), false), player.xp(), player.points(), player.extras()));
        }
        GameTeam winner = result.winnerTeam();
        String server = game.services().find(ServerLifecycle.class).map(lifecycle -> lifecycle.self().name())
                .orElse(game.serverName());
        MatchResult match = new MatchResult(game.matchId(), ModeKey.of(game.definition().modeId()),
                game.variant().key(), game.maps().current().id(), server, game.startedAt(), endedAt,
                winner == null ? null : winner.index(), stats);
        return game.services().get(StatsService.class).submit(match);
    }

    private void blocks(GameResult result, Map<UUID, Boolean> survived) {
        boolean privateRound = game.privateRound().current().isPresent();
        for (GamePlayer gamePlayer : game.players()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null || !gamePlayer.participant()) {
                continue;
            }
            if (privateRound) {
                game.texts().send(player, "round.private");
            } else {
                game.services().find(StatsService.class).ifPresent(stats -> stats.roundSummary(player,
                        game.mode().summary(gamePlayer, result)));
            }
        }
        if (game.settings().replay()) {
            String id = replayId();
            game.texts().broadcast("common.replay", Placeholder.unparsed("id", id),
                    Placeholder.styling("replay_click", ClickEvent.runCommand("/replay " + id)));
        }
    }

    private void payout(GameResult result) {
        if (game.privateRound().current().isPresent()) {
            return;
        }
        Map<UUID, Long> base = new LinkedHashMap<>();
        List<UUID> roundPlayers = new ArrayList<>();
        for (GamePlayer player : game.players()) {
            if (player.participant()) {
                base.put(player.uuid(), Math.max(0, game.mode().baseCoins(player, result)));
                roundPlayers.add(player.uuid());
            }
        }
        game.services().find(CoinService.class).ifPresent(coins -> coins.payoutRound(game.matchId(), base,
                roundPlayers).exceptionally(error -> {
                    game.log().error("Coins der Runde {} nicht gebucht", game.matchId(), error);
                    return List.of();
                }));
    }

    private void restart(RestartPlan plan, GameResult result, Instant endedAt) {
        timer.start("restart", Duration.ofSeconds(plan.total()), rest -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendExperienceChange(plan.progress(rest), rest);
                if (plan.announce(rest)) {
                    game.texts().send(player, game.texts().count("common.restart", rest),
                            Placeholder.unparsed("n", Integer.toString(rest)));
                }
            }
        }, () -> close(result, endedAt));
    }

    private void close(GameResult result, Instant endedAt) {
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        game.services().find(PlayerTransfer.class).ifPresent(transfer -> transfer.toLobby(online, "game.ended",
                game.definition().modeId()));
        List<UUID> winners = game.players().stream().filter(result::won).map(GamePlayer::uuid).toList();
        List<UUID> players = game.players().stream().filter(GamePlayer::participant).map(GamePlayer::uuid).toList();
        long duration = Math.max(0, Duration.between(game.startedAt(), endedAt).toSeconds());
        game.services().find(MessageBus.class).ifPresent(bus -> bus.publish(MessageType.GAME_ENDED,
                new GameEnded(game.matchId().toString(), game.definition().modeId(), game.variant().key(),
                        game.maps().current().id(), winners, duration, null, players)));
        game.services().find(ServerLifecycle.class).ifPresent(ServerLifecycle::incrementMatches);
        if (!game.definition().persistent()) {
            Bukkit.getScheduler().runTaskLater(game.plugin(), () -> game.services().find(ServerLifecycle.class)
                    .ifPresentOrElse(lifecycle -> lifecycle.requestShutdown("MATCH_END"), Bukkit::shutdown),
                    RestartPlan.SHUTDOWN_DELAY_SECONDS * 20L);
        }
    }

    /** Replay-ID, 8 Zeichen Base62 (Spec 03 §6.10 Nr. 7). */
    static String replayId() {
        SecureRandom random = new SecureRandom();
        StringBuilder id = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            id.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return id.toString();
    }

    public void cancel() {
        timer.cancel();
    }

    /** Platzhalter für Spieler-Text (Hilfsmethode für Modi). */
    public static TagResolver count(String tag, long value) {
        return Placeholder.unparsed(tag, Long.toString(value));
    }
}
