package net.retromc.game.framework.command;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.rank.Rank;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.end.GameResult;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.server.PlayerTransfer;
import net.retromc.paper.core.server.ServerLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * Befehle des Spiel-Rahmens (Spec 03 §3): {@code /start} (#4), {@code /forcemap}/{@code /fm} (#5), {@code /game}
 * (#14), {@code /team <farbe>} (#15) und {@code /teleporter} (Zuschauer, Spec 04 §3).
 */
public final class GameCommands {

    /** {@code /start}: Premium und höher (Spec 03 §3 #4). */
    public static final String START = BuildInfo.BRAND_ID + ".game.start";
    /** {@code /game …}. */
    public static final String ADMIN = BuildInfo.BRAND_ID + ".game.admin";

    private GameCommands() {
    }

    public static void register(Game game) {
        PaperCommandManager<Source> manager = game.services().get(CommandService.class).manager();
        manager.command(manager.commandBuilder("start").senderType(PlayerSource.class)
                .handler(ctx -> run(game, () -> start(game, ctx.sender().source()))));
        SuggestionProvider<Source> maps = SuggestionProvider.blockingStrings((ctx, input) ->
                game.maps().valid().stream().map(GameMap::displayName).toList());
        manager.command(manager.commandBuilder("forcemap", "fm").senderType(PlayerSource.class)
                .required("map", greedyStringParser(), maps)
                .handler(ctx -> run(game, () -> game.maps().forcemap(ctx.sender().source(), ctx.get("map"),
                        false))));
        SuggestionProvider<Source> teams = SuggestionProvider.blockingStrings((ctx, input) ->
                game.teams().teams().stream().map(GameTeam::id).toList());
        manager.command(manager.commandBuilder("team").senderType(PlayerSource.class)
                .required("team", stringParser(), teams)
                .handler(ctx -> run(game, () -> team(game, ctx.sender().source(), ctx.get("team")))));
        manager.command(manager.commandBuilder("teleporter").senderType(PlayerSource.class)
                .handler(ctx -> run(game, () -> game.spectators().openTeleporter(ctx.sender().source()))));
        manager.command(manager.commandBuilder("game").literal("forcestart").permission(ADMIN)
                .senderType(PlayerSource.class).handler(ctx -> run(game, () -> {
                    Player player = ctx.sender().source();
                    game.texts().send(player, game.waiting().forceStart() ? "game.admin.forcestart"
                            : "game.admin.forcestart.fail");
                })));
        manager.command(manager.commandBuilder("game").literal("stop").permission(ADMIN)
                .senderType(PlayerSource.class).handler(ctx -> run(game, () -> stop(game, ctx.sender().source()))));
        manager.command(manager.commandBuilder("game").literal("setmap").permission(ADMIN)
                .senderType(PlayerSource.class).required("map", greedyStringParser(), maps)
                .handler(ctx -> run(game, () -> game.maps().forcemap(ctx.sender().source(), ctx.get("map"),
                        true))));
        manager.command(manager.commandBuilder("game").literal("state").permission(ADMIN)
                .senderType(PlayerSource.class).handler(ctx -> run(game, () -> state(game, ctx.sender().source()))));
    }

    /** Befehle laufen auf dem Haupt-Thread (Bukkit-API). */
    private static void run(Game game, Runnable action) {
        game.mainThread().execute(action);
    }

    private static void start(Game game, Player player) {
        boolean owner = game.privateRound().isOwner(player);
        if (!owner && !player.hasPermission(START)
                && game.texts().rankWeight(player.getUniqueId()) < Rank.PREMIUM.weight()) {
            game.texts().send(player, "common.noperm");
            return;
        }
        game.waiting().startCommand(player, owner);
    }

    private static void team(Game game, Player player, String input) {
        game.teams().byId(input.toLowerCase(Locale.ROOT)).ifPresentOrElse(team -> game.chooseTeam(player, team),
                () -> game.texts().send(player, "game.team.unknown", Placeholder.unparsed("team", input)));
    }

    private static void stop(Game game, Player player) {
        game.texts().send(player, "game.admin.stop");
        if (game.state() == GameState.INGAME) {
            game.finish(GameResult.ofDraw());
            return;
        }
        if (game.state() != GameState.ENDING) {
            List<java.util.UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            game.services().find(PlayerTransfer.class).ifPresent(transfer -> transfer.toLobby(online, "game.ended",
                    game.definition().modeId()));
            game.services().find(ServerLifecycle.class).ifPresent(lifecycle -> lifecycle.requestShutdown("ADMIN"));
        }
    }

    private static void state(Game game, Player player) {
        String map = game.maps().currentIfSet().map(GameMap::displayName).orElse("-");
        game.texts().send(player, "game.admin.state", Placeholder.unparsed("phase", game.state().name()),
                Placeholder.unparsed("n", Integer.toString(Bukkit.getOnlinePlayers().size())),
                Placeholder.unparsed("max", Integer.toString(game.maxPlayers())),
                Placeholder.unparsed("map", map),
                Placeholder.unparsed("rest", game.state() == GameState.STARTING
                        ? Integer.toString(game.countdownRest()) : "-"));
    }
}
