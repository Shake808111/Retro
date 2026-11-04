package net.retromc.skywars;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import java.util.ArrayList;
import java.util.List;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.games.KitStore;
import net.retromc.game.framework.GameState;
import net.retromc.paper.core.command.CommandService;
import net.retromc.skywars.event.GlobalEventSchedule;
import net.retromc.skywars.kit.KitDef;
import net.retromc.skywars.kit.KitGui;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.SuggestionProvider;

/**
 * SkyWars-Befehle (Spec 04 §3): {@code /kit [Name]} ({@code /kits}) und {@code /swdebug event <id>};
 * {@code /swsetup} liefert {@code SkyWarsSetup}, {@code /start}, {@code /forcemap}, {@code /teleporter} der Rahmen.
 */
final class SkyWarsCommands {

    /** Recht für {@code /swdebug}. */
    static final String DEBUG = BuildInfo.BRAND_ID + ".admin.debug";

    private SkyWarsCommands() {
    }

    static void register(SkyWarsMode mode) {
        PaperCommandManager<Source> manager = mode.game().services().get(CommandService.class).manager();
        SuggestionProvider<PlayerSource> kits = SuggestionProvider.suggestingStrings(names(mode));
        for (String name : List.of("kit", "kits")) {
            manager.command(manager.commandBuilder(name).senderType(PlayerSource.class)
                    .handler(ctx -> run(mode, () -> kit(mode, ctx.sender().source(), null))));
            manager.command(manager.commandBuilder(name).senderType(PlayerSource.class)
                    .required("kit", stringParser(), kits)
                    .handler(ctx -> run(mode, () -> kit(mode, ctx.sender().source(), ctx.get("kit")))));
        }
        manager.command(manager.commandBuilder("swdebug").literal("event").permission(DEBUG)
                .senderType(PlayerSource.class)
                .required("id", stringParser(), SuggestionProvider.suggestingStrings(mode.schedule().entries()
                        .stream().map(GlobalEventSchedule.Entry::id).toList()))
                .handler(ctx -> run(mode, () -> {
                    Player player = ctx.sender().source();
                    var entry = mode.schedule().byId(ctx.get("id"));
                    if (entry.isEmpty() || mode.game().state() != GameState.INGAME) {
                        mode.texts().send(player, "sw.debug.event.fail");
                        return;
                    }
                    mode.trigger(entry.get());
                    mode.texts().send(player, "sw.debug.event");
                })));
    }

    /** {@code /kit}: ohne Namen Kit-Menü, mit Namen Auswahl (nur Wartelobby). */
    private static void kit(SkyWarsMode mode, Player player, @org.jspecify.annotations.Nullable String name) {
        if (!mode.game().lifecycle().waiting()) {
            mode.texts().send(player, "sw.kit.lobbyonly");
            return;
        }
        if (name == null) {
            KitGui.open(mode.game(), mode.kits(), player);
        } else {
            mode.kits().select(player, name);
        }
    }

    private static List<String> names(SkyWarsMode mode) {
        List<String> names = new ArrayList<>();
        mode.kits().catalog().kits().stream().map(KitDef::id).forEach(names::add);
        names.add(KitStore.RANDOM);
        return names;
    }

    private static void run(SkyWarsMode mode, Runnable action) {
        mode.game().mainThread().execute(action);
    }
}
