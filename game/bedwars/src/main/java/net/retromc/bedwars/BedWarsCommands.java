package net.retromc.bedwars;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import java.util.Arrays;
import net.retromc.bedwars.setup.BedWarsSetup;
import net.retromc.bedwars.shop.ShopGui;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.core.api.BuildInfo;
import net.retromc.paper.core.command.CommandService;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * BedWars-Befehle (Spec 04 §3): {@code /shopsettings}, {@code /quickshop}, {@code /goldvote [save]},
 * {@code /bwsetup …} und {@code /bwdebug spawner <res> <tier>}. {@code /start}, {@code /forcemap},
 * {@code /teleporter} liefert der Spiel-Rahmen, {@code @a}/{@code @all} der Chat des Rahmens.
 */
final class BedWarsCommands {

    /** Recht für {@code /bwdebug}. */
    static final String DEBUG = BuildInfo.BRAND_ID + ".admin.debug";

    private BedWarsCommands() {
    }

    static void register(BedWarsMode mode) {
        PaperCommandManager<Source> manager = mode.game().services().get(CommandService.class).manager();
        manager.command(manager.commandBuilder("shopsettings").senderType(PlayerSource.class)
                .handler(ctx -> run(mode, () -> waiting(mode, ctx.sender().source(),
                        () -> ShopSettingsGui.open(mode, ctx.sender().source())))));
        manager.command(manager.commandBuilder("quickshop").senderType(PlayerSource.class)
                .handler(ctx -> run(mode, () -> waiting(mode, ctx.sender().source(),
                        () -> ShopGui.open(mode, ctx.sender().source(), ShopGui.Mode.EDIT)))));
        manager.command(manager.commandBuilder("goldvote").senderType(PlayerSource.class)
                .handler(ctx -> run(mode, () -> mode.goldVote().open(ctx.sender().source()))));
        manager.command(manager.commandBuilder("goldvote").literal("save").senderType(PlayerSource.class)
                .handler(ctx -> run(mode, () -> mode.goldVote().save(ctx.sender().source()))));
        manager.command(manager.commandBuilder("bwdebug").literal("spawner").permission(DEBUG)
                .senderType(PlayerSource.class)
                .required("resource", stringParser(), SuggestionProvider.suggestingStrings(
                        Arrays.asList("diamond", "gold")))
                .required("tier", integerParser(1, 3))
                .handler(ctx -> run(mode, () -> {
                    Player player = ctx.sender().source();
                    boolean set = Resource.byId(ctx.get("resource")).map(resource -> mode.generators()
                            .forceTier(resource, ctx.<Integer>get("tier"))).orElse(false);
                    mode.texts().send(player, set ? "bw.debug.tier" : "bw.setup.usage");
                })));
        BedWarsSetup.register(mode);
    }

    private static void waiting(BedWarsMode mode, Player player, Runnable action) {
        if (!mode.game().lifecycle().waiting()) {
            mode.texts().send(player, "common.not_in_lobby");
            return;
        }
        action.run();
    }

    private static void run(BedWarsMode mode, Runnable action) {
        mode.game().mainThread().execute(action);
    }
}
