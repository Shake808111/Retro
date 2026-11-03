package net.retromc.velocity.core.msg;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.event.PrivateMessage;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.IgnoreOutcome;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.MessageResult;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.PrivateMessageService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import net.retromc.velocity.core.spam.ProxyChat;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Private Nachrichten und Ignorieren (Spec 01 §3.2 #16–21, §6.7): {@code /msg} (Aliase {@code /tell},
 * {@code /whisper}), {@code /msg list}, {@code /r} ({@code /reply}), {@code /ignore [remove|list]},
 * {@code /unignore}. Mute und Spam prüft {@link ProxyChat}; Zustellung an den Proxy des Empfängers über
 * {@code PRIVATE_MSG}. Genickte Spieler sind über ihren Nick nur für Freunde und Team erreichbar.
 */
public final class MsgFeature implements CoreFeature<VelocityCore> {

    public static final CommandName MSG = CommandName.of("msg", "tell", "whisper");
    public static final CommandName REPLY = CommandName.of("r", "reply");
    public static final CommandName IGNORE = CommandName.of("ignore");
    public static final CommandName UNIGNORE = CommandName.of("unignore");
    public static final List<CommandName> COMMANDS = List.of(MSG, REPLY, IGNORE, UNIGNORE);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of(
            "Europe/Berlin"));

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;
    private @Nullable ProxyChat chat;

    @Override
    public String name() {
        return "Nachrichten";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        chat = new ProxyChat(helper);
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        manager.command(MSG.builder(manager).literal("list").senderType(Player.class)
                .handler(ctx -> list(ctx.sender())));
        manager.command(MSG.builder(manager).senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .required("text", greedyStringParser())
                .handler(ctx -> message(ctx.sender(), ctx.get("player"), ctx.get("text"))));
        manager.command(REPLY.builder(manager).senderType(Player.class)
                .required("text", greedyStringParser())
                .handler(ctx -> reply(ctx.sender(), ctx.get("text"))));
        manager.command(IGNORE.builder(manager).literal("list").senderType(Player.class)
                .handler(ctx -> ignoreList(ctx.sender())));
        manager.command(IGNORE.builder(manager).literal("remove").senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .handler(ctx -> unignore(ctx.sender(), ctx.get("player"))));
        manager.command(IGNORE.builder(manager).senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .handler(ctx -> ignore(ctx.sender(), ctx.get("player"))));
        manager.command(UNIGNORE.builder(manager).senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .handler(ctx -> unignore(ctx.sender(), ctx.get("player"))));
        subscriptions.add(core.services().get(MessageBus.class)
                .subscribe(MessageType.PRIVATE_MSG, envelope -> deliver(envelope.d())).join());
    }

    @Override
    public void disable(VelocityCore core) {
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
        chat = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Nachrichten sind nicht aktiv");
        }
        return current;
    }

    // ------------------------------------------------------------------ /msg, /r

    void message(Player sender, String name, String text) {
        Social helper = social();
        helper.then(sender, target(sender, name), target -> {
            if (target.isEmpty()) {
                helper.send(sender, "msg.err.offline", Social.player(name));
                return;
            }
            send(sender, target.get(), text, false);
        });
    }

    void reply(Player sender, String text) {
        send(sender, null, text, true);
    }

    /**
     * Empfänger von {@code /msg <Name>}: echter Spieler (Proxy oder Profil); sonst ein Nick, der nur für Freunde und
     * Team auflösbar ist (Spec 01 §6.7 Nr. 4).
     */
    CompletableFuture<Optional<PlayerRef>> target(Player sender, String name) {
        Social helper = social();
        return helper.resolve(name).thenCompose(real -> {
            if (real.isPresent()) {
                return CompletableFuture.completedFuture(real);
            }
            return helper.get(NickService.class).resolve(name).thenCompose(nicked -> {
                if (nicked.isEmpty()) {
                    return CompletableFuture.completedFuture(Optional.<PlayerRef>empty());
                }
                UUID uuid = nicked.get();
                return helper.isTeam(sender.getUniqueId())
                        .thenCombine(helper.get(FriendService.class).areFriends(sender.getUniqueId(), uuid)
                                .exceptionally(error -> false), (team, friend) -> team || friend)
                        .thenApply(allowed -> allowed ? Optional.of(new PlayerRef(uuid, name)) : Optional.empty());
            });
        });
    }

    private void send(Player sender, @Nullable PlayerRef target, String text, boolean reply) {
        Social helper = social();
        ProxyChat guard = chat;
        if (guard == null) {
            return;
        }
        helper.then(sender, guard.check(sender, text, ChatContext.Source.PRIVATE_MESSAGE), checked -> {
            if (checked.isEmpty()) {
                return;
            }
            PrivateMessageService messages = helper.get(PrivateMessageService.class);
            CompletableFuture<MessageResult> result = reply || target == null
                    ? messages.reply(sender.getUniqueId(), checked.get())
                    : messages.send(sender.getUniqueId(), target.uuid(), checked.get());
            helper.then(sender, result, outcome -> answer(sender, target, outcome, checked.get()));
        });
    }

    private void answer(Player sender, @Nullable PlayerRef target, MessageResult result, String text) {
        Social helper = social();
        String name = target == null ? "?" : target.name();
        switch (result.outcome()) {
            case DELIVERED -> {
                UUID recipient = result.recipient();
                CompletableFuture<String> recipientName = target != null
                        ? CompletableFuture.completedFuture(target.name()) : helper.nameOf(recipient);
                helper.then(sender, recipientName.thenCompose(known -> helper.coloredName(recipient, known)),
                        colored -> helper.send(sender, "msg.out", Placeholder.component("name", colored),
                                Placeholder.unparsed("text", text)));
            }
            case SELF -> helper.send(sender, "msg.err.self");
            case OFFLINE -> {
                if (target == null) {
                    helper.send(sender, "msg.err.noreply");
                } else {
                    helper.send(sender, "msg.err.offline", Social.player(name));
                }
            }
            case DENIED -> helper.send(sender, "msg.err.denied", Social.player(name));
            case NO_REPLY_TARGET -> helper.send(sender, "msg.err.noreply");
            default -> throw new IllegalStateException("Unbekanntes Ergebnis " + result.outcome());
        }
    }

    /** {@code PRIVATE_MSG}: an Empfänger auf diesem Proxy zustellen ({@code msg.in}). */
    void deliver(PrivateMessage message) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        for (UUID recipient : message.to()) {
            helper.online(recipient).ifPresent(player -> helper.log(helper.nameOf(message.from())
                    .thenCompose(name -> helper.coloredName(message.from(), name))
                    .thenAccept(colored -> helper.send(player, "msg.in", Placeholder.component("name", colored),
                            Placeholder.unparsed("text", message.text()))), "PN zustellen"));
        }
    }

    void list(Player player) {
        Social helper = social();
        helper.then(player, helper.get(PrivateMessageService.class).recent(player.getUniqueId()), messages -> {
            helper.send(player, "msg.list.header");
            if (messages.isEmpty()) {
                helper.send(player, "msg.list.empty");
                return;
            }
            messages.forEach(message -> helper.then(player, helper.nameOf(message.from())
                    .thenCompose(name -> helper.coloredName(message.from(), name)), colored -> helper.send(player,
                            "msg.list.line", Social.unparsed("time", TIME.format(message.ts())),
                            Placeholder.component("name", colored), Placeholder.unparsed("text", message.text()))));
        });
    }

    // ------------------------------------------------------------------ /ignore

    void ignore(Player owner, String name) {
        Social helper = social();
        helper.then(owner, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(owner, "error.player.unknown", Social.player(name));
                return;
            }
            helper.then(owner, helper.get(IgnoreService.class).add(owner.getUniqueId(), target.get().uuid()),
                    outcome -> helper.send(owner, ignoreKey(outcome), Social.player(target.get().name())));
        });
    }

    static String ignoreKey(IgnoreOutcome outcome) {
        return switch (outcome) {
            case ADDED -> "ignore.added";
            case SELF -> "ignore.err.self";
            case UNKNOWN_PLAYER -> "error.player.unknown";
            case STAFF -> "ignore.err.staff";
            case LIMIT -> "ignore.err.limit";
            case ALREADY -> "ignore.err.already";
        };
    }

    void unignore(Player owner, String name) {
        Social helper = social();
        helper.then(owner, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(owner, "error.player.unknown", Social.player(name));
                return;
            }
            helper.then(owner, helper.get(IgnoreService.class).remove(owner.getUniqueId(), target.get().uuid()),
                    removed -> helper.send(owner, removed ? "ignore.removed" : "ignore.err.notignored",
                            Social.player(target.get().name())));
        });
    }

    void ignoreList(Player owner) {
        Social helper = social();
        helper.then(owner, helper.get(IgnoreService.class).list(owner.getUniqueId()), list -> {
            if (list.isEmpty()) {
                helper.send(owner, "ignore.list.empty");
                return;
            }
            helper.send(owner, "ignore.list.header", Social.unparsed("n", list.size()));
            list.forEach(entry -> helper.send(owner, "ignore.list.line", Social.player(entry.name())));
        });
    }
}
