package net.retromc.velocity.core.joinme;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.retromc.core.api.event.Broadcast;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.social.JoinMeService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.config.ServerNames;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.ModerationTexts;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * {@code /joinme} (Spec 01 §3.5 #66, §6.17): ab Supremium, 3×/Tag (Team unbegrenzt, {@code RankLimits.joinmePerDay}),
 * nur aus der Wartephase eines Spielservers, gesperrt bei Mute. Broadcast {@code joinme.broadcast} mit [BETRETEN] an
 * alle Lobbys außer der Silent-Lobby über {@code BROADCAST}; der Klick verbindet mit dem Server.
 */
public final class JoinMeFeature implements CoreFeature<VelocityCore> {

    public static final CommandName JOINME = CommandName.of("joinme");
    public static final List<CommandName> COMMANDS = List.of(JOINME);

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;

    @Override
    public String name() {
        return "JoinMe";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var joinMe = JOINME.builder(manager).senderType(Player.class);
        manager.command(joinMe.handler(ctx -> joinMe(ctx.sender())));
        manager.command(joinMe.literal("join").required("server", stringParser())
                .handler(ctx -> core.proxy().getServer(ctx.<String>get("server"))
                        .ifPresent(server -> ctx.sender().createConnectionRequest(server).fireAndForget())));
        subscriptions.add(core.services().get(MessageBus.class).subscribe(MessageType.BROADCAST,
                envelope -> deliver(envelope.d())).join());
    }

    @Override
    public void disable(VelocityCore core) {
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("JoinMe ist nicht aktiv");
        }
        return current;
    }

    /** Wartephase eines Spielservers (ohne Server-Registry: jeder Spielserver). */
    static boolean waitingRound(ServerCategory category, Optional<ServerInfo> info) {
        return category == ServerCategory.GAME && info.map(ServerInfo::state)
                .map(state -> state == ServerState.WAITING || state == ServerState.STARTING).orElse(true);
    }

    void joinMe(Player sender) {
        Social helper = social();
        Optional<String> server = Social.serverName(sender);
        if (server.isEmpty() || !waitingRound(helper.category(server.get()), helper.registryInfo(server.get()))) {
            helper.send(sender, "joinme.err.lobby");
            return;
        }
        helper.then(sender, helper.get(RankService.class).rank(sender.getUniqueId()), rank -> {
            int perDay = rank.limits().joinmePerDay();
            if (perDay == 0) {
                helper.send(sender, "error.rank.required", Placeholder.component("rank",
                        Social.coloredName(RankInfo.of(Rank.SUPREMIUM), Rank.SUPREMIUM.displayName())));
                return;
            }
            helper.then(sender, helper.get(PunishmentService.class).activeMute(sender.getUniqueId()), mute -> {
                if (mute.isPresent()) {
                    sender.sendMessage(new ModerationTexts(helper.get(I18n.class)).muteNotice(helper.locale(sender),
                            mute.get(), Instant.now()));
                    return;
                }
                helper.then(sender, helper.get(JoinMeService.class).use(sender.getUniqueId(), perDay), result -> {
                    if (!result.allowed()) {
                        helper.send(sender, "joinme.err.limit");
                        return;
                    }
                    Map<String, String> args = new LinkedHashMap<>();
                    args.put("name", rank.legacyColor() + sender.getUsername());
                    args.put("mode", helper.mode(server.get()));
                    args.put("server", server.get());
                    helper.log(helper.get(MessageBus.class).publish(MessageType.BROADCAST, new Broadcast(
                            "joinme.broadcast", args, "/joinme join " + server.get(), true)), "JoinMe senden");
                    helper.send(sender, "joinme.sent", Social.unparsed("used", result.used()),
                            Social.unparsed("max", result.max() < 0 ? "∞" : Integer.toString(result.max())));
                });
            });
        });
    }

    /** Ist der Server eine Lobby (ohne Silent-Lobby, Spec 01 §6.8)? */
    boolean lobby(String server) {
        Social helper = social();
        Optional<ServerInfo> info = helper.registryInfo(server);
        if (info.isPresent()) {
            return info.get().category() == ServerCategory.LOBBY && !info.get().silent();
        }
        return helper.category(server) == ServerCategory.LOBBY
                && !ServerNames.templateOf(server).map(TemplateId.SILENT_LOBBY::equals).orElse(false);
    }

    /** {@code BROADCAST}: Text (Argumente als §-Text) mit optionaler Klickzeile an Spieler dieses Proxys. */
    void deliver(Broadcast broadcast) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        List<TagResolver> placeholders = new ArrayList<>();
        broadcast.args().forEach((key, value) -> placeholders.add(Placeholder.component(key,
                LegacyComponentSerializer.legacySection().deserialize(value))));
        TagResolver resolver = TagResolver.resolver(placeholders);
        for (Player player : helper.core().proxy().getAllPlayers()) {
            Optional<String> server = Social.serverName(player);
            if (broadcast.lobbiesOnly() && (server.isEmpty() || !lobby(server.get()))) {
                continue;
            }
            helper.send(player, broadcast.key(), resolver);
            if (broadcast.clickCommand() != null) {
                player.sendMessage(helper.clickLine(player, "prefix.joinme", "click.join", broadcast.clickCommand()));
            }
        }
    }
}
