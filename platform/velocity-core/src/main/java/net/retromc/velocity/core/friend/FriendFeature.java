package net.retromc.velocity.core.friend;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.event.FriendNotice;
import net.retromc.core.api.event.FriendStatus;
import net.retromc.core.api.social.FriendEntry;
import net.retromc.core.api.social.FriendOutcome;
import net.retromc.core.api.social.FriendResult;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.JumpResult;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.bridge.ServerBridge;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Freunde (Spec 01 §3.1 #1–15, §6.3 Nr. 6/7, §6.4): {@code /friend} ({@code /friends}) mit allen Unterbefehlen,
 * {@code /fr}; Anfragen, Annahmen und Entfernen werden über {@code net:social} an den Proxy des Ziels gemeldet,
 * Online-/Offline-Meldungen an Freunde mit {@code notify_friends} bzw. Favorit-{@code notify}. Die Regeln setzt der
 * {@link FriendService} um, die GUIs §5.2/§5.3 öffnet paper-core.
 */
public final class FriendFeature implements CoreFeature<VelocityCore> {

    public static final CommandName FRIEND = CommandName.of("friend", "friends");
    public static final CommandName FR = CommandName.of("fr");
    public static final List<CommandName> COMMANDS = List.of(FRIEND, FR);

    /** GUI-IDs auf paper-core (§5.2, §5.3). */
    public static final String GUI_FRIENDS = "friends";
    public static final String GUI_REQUESTS = "friends.requests";

    private final List<Registration> subscriptions = new ArrayList<>();
    private final Map<UUID, Instant> clearRequests = new ConcurrentHashMap<>();
    private @Nullable Social social;

    @Override
    public String name() {
        return "Freunde";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var friend = FRIEND.builder(manager).senderType(Player.class);
        manager.command(friend.handler(ctx -> helper.send(ctx.sender(), "help.friend.1")));
        manager.command(friend.literal("2").handler(ctx -> helper.send(ctx.sender(), "help.friend.2")));
        manager.command(friend.literal("add").required("player", stringParser(), helper.players())
                .handler(ctx -> add(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("accept").required("player", stringParser())
                .handler(ctx -> accept(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("deny").required("player", stringParser())
                .handler(ctx -> deny(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("acceptall", "acceptAll").handler(ctx -> acceptAll(ctx.sender())));
        manager.command(friend.literal("denyall", "denyAll").handler(ctx -> denyAll(ctx.sender())));
        manager.command(friend.literal("requests").handler(ctx -> requests(ctx.sender())));
        manager.command(FR.builder(manager).senderType(Player.class).handler(ctx -> requests(ctx.sender())));
        manager.command(friend.literal("remove").required("player", stringParser())
                .handler(ctx -> remove(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("list").optional("page", integerParser(1))
                .handler(ctx -> list(ctx.sender(), ctx.<Integer>optional("page").orElse(1))));
        manager.command(friend.literal("jump").required("player", stringParser())
                .handler(ctx -> jump(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("favorite", "fav").required("player", stringParser())
                .handler(ctx -> favorite(ctx.sender(), ctx.get("player"))));
        manager.command(friend.literal("toggleonline").handler(ctx -> toggle(ctx.sender(), Setting.SHOW_ONLINE)));
        manager.command(friend.literal("toggle").handler(ctx -> toggle(ctx.sender(), Setting.FRIEND_REQUESTS)));
        manager.command(friend.literal("clear").handler(ctx -> clear(ctx.sender(), false)));
        manager.command(friend.literal("clear").literal("confirm").handler(ctx -> clear(ctx.sender(), true)));
        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.FRIEND_REQUEST, envelope -> onRequest(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.FRIEND_ADDED, envelope -> onNotice(envelope.d(),
                "friend.added")).join());
        subscriptions.add(bus.subscribe(MessageType.FRIEND_REMOVED, envelope -> onNotice(envelope.d(),
                "friend.removed")).join());
        subscriptions.add(bus.subscribe(MessageType.FRIEND_ONLINE, envelope -> onStatus(envelope.d(),
                "friend.online")).join());
        subscriptions.add(bus.subscribe(MessageType.FRIEND_OFFLINE, envelope -> onStatus(envelope.d(),
                "friend.offline")).join());
        subscriptions.add(bus.subscribe(MessageType.FRIEND_SPECTATE, envelope -> onStatus(envelope.d(),
                "friend.spectate")).join());
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        clearRequests.clear();
        social = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Freunde sind nicht aktiv");
        }
        return current;
    }

    private FriendService friends() {
        return social().get(FriendService.class);
    }

    private void withPlayer(Player sender, String name, Consumer<PlayerRef> action) {
        Social helper = social();
        helper.then(sender, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(sender, "error.player.unknown", Social.player(name));
            } else {
                action.accept(target.get());
            }
        });
    }

    /** Text mit {@code <name>} in Rangfarbe. */
    private void sendNamed(Player receiver, String key, PlayerRef named) {
        Social helper = social();
        helper.then(receiver, helper.coloredName(named.uuid(), named.name()), colored -> helper.send(receiver, key,
                Placeholder.component("name", colored)));
    }

    /** Fehlertexte (Spec 01 §3.1 Ausgaben). */
    static String errorKey(FriendOutcome outcome) {
        return switch (outcome) {
            case SELF -> "friend.err.self";
            case UNKNOWN_PLAYER -> "error.player.unknown";
            case ALREADY_FRIENDS -> "friend.err.already";
            case ALREADY_PENDING -> "friend.err.pending";
            case NO_REQUEST -> "friend.err.norequest";
            case NOT_FRIENDS -> "friend.err.notfriends";
            case LIMIT_SELF -> "friend.err.limit.self";
            case LIMIT_OTHER -> "friend.err.limit.other";
            case BLOCKED -> "friend.err.blocked";
            case SENT, ADDED, DENIED, REMOVED -> throw new IllegalArgumentException("Kein Fehler: " + outcome);
        };
    }

    private void error(Player sender, FriendResult result, PlayerRef target) {
        Social helper = social();
        helper.then(sender, helper.coloredName(target.uuid(), target.name()), colored -> helper.send(sender,
                errorKey(result.outcome()), Placeholder.component("name", colored), Social.player(target.name()),
                Social.unparsed("max", result.limit())));
    }

    private void notice(MessageType<FriendNotice> type, UUID actor, UUID target) {
        Social helper = social();
        helper.log(helper.get(MessageBus.class).publish(type, new FriendNotice(actor, target)), "Freunde-Ereignis");
    }

    // ------------------------------------------------------------------ Befehle

    void add(Player sender, String name) {
        withPlayer(sender, name, target -> social().then(sender, friends().request(sender.getUniqueId(),
                target.uuid()), result -> {
                    switch (result.outcome()) {
                        case SENT -> {
                            sendNamed(sender, "friend.request.sent", target);
                            notice(MessageType.FRIEND_REQUEST, sender.getUniqueId(), target.uuid());
                        }
                        case ADDED -> notice(MessageType.FRIEND_ADDED, sender.getUniqueId(), target.uuid());
                        default -> error(sender, result, target);
                    }
                }));
    }

    void accept(Player sender, String name) {
        withPlayer(sender, name, target -> social().then(sender, friends().accept(sender.getUniqueId(),
                target.uuid()), result -> {
                    if (result.outcome() == FriendOutcome.ADDED) {
                        notice(MessageType.FRIEND_ADDED, sender.getUniqueId(), target.uuid());
                    } else {
                        error(sender, result, target);
                    }
                }));
    }

    void deny(Player sender, String name) {
        withPlayer(sender, name, target -> social().then(sender, friends().deny(sender.getUniqueId(),
                target.uuid()), result -> {
                    if (result.outcome() == FriendOutcome.DENIED) {
                        sendNamed(sender, "friend.denied", target);
                    } else {
                        error(sender, result, target);
                    }
                }));
    }

    void acceptAll(Player sender) {
        Social helper = social();
        helper.then(sender, friends().requests(sender.getUniqueId()).thenCompose(open -> friends()
                .acceptAll(sender.getUniqueId()).thenApply(count -> {
                    open.stream().limit(count).forEach(request -> notice(MessageType.FRIEND_ADDED,
                            sender.getUniqueId(), request.sender()));
                    return count;
                })), count -> helper.send(sender, "friend.acceptall", Social.unparsed("n", count)));
    }

    void denyAll(Player sender) {
        Social helper = social();
        helper.then(sender, friends().denyAll(sender.getUniqueId()),
                count -> helper.send(sender, "friend.denyall", Social.unparsed("n", count)));
    }

    /** {@code /fr}: GUI §5.3 auf paper-core, sonst Chatliste mit Klickzeilen. */
    void requests(Player sender) {
        Social helper = social();
        if (helper.get(ServerBridge.class).openGui(sender, GUI_REQUESTS, "")) {
            return;
        }
        helper.then(sender, friends().requests(sender.getUniqueId()), open -> open.forEach(request -> {
            sendNamed(sender, "friend.request.received", new PlayerRef(request.sender(), request.senderName()));
            sender.sendMessage(helper.clickLine(sender, "prefix.friends", "click.accept",
                    "/friend accept " + request.senderName(), "click.deny", "/friend deny " + request.senderName()));
        }));
    }

    void remove(Player sender, String name) {
        withPlayer(sender, name, target -> social().then(sender, friends().remove(sender.getUniqueId(),
                target.uuid()), result -> {
                    if (result.outcome() == FriendOutcome.REMOVED) {
                        notice(MessageType.FRIEND_REMOVED, sender.getUniqueId(), target.uuid());
                    } else {
                        error(sender, result, target);
                    }
                }));
    }

    void list(Player sender, int page) {
        Social helper = social();
        helper.then(sender, friends().list(sender.getUniqueId()), entries -> {
            if (entries.isEmpty()) {
                helper.send(sender, "friend.list.empty");
                return;
            }
            List<FriendEntry> sorted = entries.stream().sorted(FriendEntry.LIST_ORDER).toList();
            int pages = Math.max(1, (sorted.size() + FriendService.LIST_PAGE_SIZE - 1) / FriendService.LIST_PAGE_SIZE);
            int current = Math.min(page, pages);
            long online = sorted.stream().filter(FriendEntry::online).count();
            helper.send(sender, "friend.list.header", Social.unparsed("online", online),
                    Social.unparsed("total", sorted.size()), Social.unparsed("page", current),
                    Social.unparsed("pages", pages));
            sorted.stream().skip((long) (current - 1) * FriendService.LIST_PAGE_SIZE)
                    .limit(FriendService.LIST_PAGE_SIZE).forEachOrdered(entry -> helper.then(sender,
                            helper.coloredName(entry.uuid(), entry.name()), colored -> helper.send(sender,
                                    "friend.list.line", Placeholder.component("name", colored),
                                    Formatter.booleanChoice("online", entry.online()),
                                    Social.unparsed("server", entry.server() == null ? "" : entry.server()))));
        });
    }

    void jump(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, friends().jump(sender.getUniqueId(), target.uuid()),
                result -> {
                    String key = switch (result.outcome()) {
                        case OK -> "friend.jump.ok";
                        case DENIED -> "friend.jump.err.denied";
                        case OFFLINE -> "friend.jump.err.offline";
                        case SAME_SERVER -> "friend.jump.err.same";
                        case NOT_FRIENDS -> "friend.err.notfriends";
                    };
                    sendNamed(sender, key, target);
                    if (result.outcome() == JumpResult.Outcome.OK && result.server() != null) {
                        helper.core().proxy().getServer(result.server())
                                .ifPresent(server -> sender.createConnectionRequest(server).fireAndForget());
                    }
                }));
    }

    void favorite(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, friends().areFriends(sender.getUniqueId(),
                target.uuid()), friendsNow -> {
                    if (!friendsNow) {
                        sendNamed(sender, "friend.err.notfriends", target);
                        return;
                    }
                    helper.then(sender, friends().favorite(sender.getUniqueId(), target.uuid()),
                            on -> sendNamed(sender, on ? "friend.fav.on" : "friend.fav.off", target));
                }));
    }

    void toggle(Player sender, Setting setting) {
        Social helper = social();
        CompletableFuture<PrivacySettings> changed = setting == Setting.FRIEND_REQUESTS
                ? helper.get(SettingsService.class).update(sender.getUniqueId(),
                        current -> current.toggleFriendRequests())
                : helper.get(SettingsService.class).cycle(sender.getUniqueId(), setting);
        helper.then(sender, changed, settings -> helper.send(sender, setting == Setting.SHOW_ONLINE
                ? settings.showOnline() ? "friend.online.visible" : "friend.online.hidden"
                : settings.friendRequests() ? "friend.toggle.on" : "friend.toggle.off"));
    }

    void clear(Player sender, boolean confirmed) {
        Social helper = social();
        UUID uuid = sender.getUniqueId();
        Instant requested = clearRequests.remove(uuid);
        if (!confirmed || requested == null
                || Duration.between(requested, Instant.now()).compareTo(FriendService.CLEAR_CONFIRM_WINDOW) > 0) {
            helper.then(sender, friends().counts(uuid), counts -> {
                clearRequests.put(uuid, Instant.now());
                helper.send(sender, "friend.clear.confirm", Social.unparsed("n", counts.total()));
            });
            return;
        }
        helper.then(sender, friends().clear(uuid), count -> helper.send(sender, "friend.clear.done"));
    }

    // ------------------------------------------------------------------ Zustellung

    void onRequest(FriendNotice notice) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.online(notice.target()).ifPresent(target -> helper.log(helper.nameOf(notice.actor())
                .thenAccept(name -> {
                    sendNamed(target, "friend.request.received", new PlayerRef(notice.actor(), name));
                    target.sendMessage(helper.clickLine(target, "prefix.friends", "click.accept",
                            "/friend accept " + name, "click.deny", "/friend deny " + name));
                }), "Freundschaftsanfrage zustellen"));
    }

    /** {@code friend.added}/{@code friend.removed} an beide Seiten, jeweils mit dem Namen der anderen. */
    void onNotice(FriendNotice notice, String key) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.online(notice.actor()).ifPresent(player -> helper.log(helper.nameOf(notice.target())
                .thenAccept(name -> sendNamed(player, key, new PlayerRef(notice.target(), name))), key));
        helper.online(notice.target()).ifPresent(player -> helper.log(helper.nameOf(notice.actor())
                .thenAccept(name -> sendNamed(player, key, new PlayerRef(notice.actor(), name))), key));
    }

    void onStatus(FriendStatus status, String key) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        List<Player> local = status.targets().stream().map(helper::online).flatMap(Optional::stream)
                .toList();
        if (local.isEmpty()) {
            return;
        }
        helper.log(helper.nameOf(status.uuid()).thenAccept(name -> local.forEach(player -> sendNamed(player, key,
                new PlayerRef(status.uuid(), name)))), "Freunde-Status zustellen");
    }

    /** Ankunft im Netzwerk (erster Server): {@code FRIEND_ONLINE} (Spec 01 §6.3 Nr. 6). */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getPreviousServer().isEmpty()) {
            publishStatus(event.getPlayer().getUniqueId(), FriendStatus.Status.ONLINE);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        clearRequests.remove(event.getPlayer().getUniqueId());
        publishStatus(event.getPlayer().getUniqueId(), FriendStatus.Status.OFFLINE);
    }

    private void publishStatus(UUID player, FriendStatus.Status status) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.get(FriendService.class).statusRecipients(player).thenCompose(targets -> targets.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : helper.get(MessageBus.class).publish(status == FriendStatus.Status.ONLINE
                        ? MessageType.FRIEND_ONLINE : MessageType.FRIEND_OFFLINE,
                        new FriendStatus(player, status, targets))), "Freunde-Status senden");
    }
}
