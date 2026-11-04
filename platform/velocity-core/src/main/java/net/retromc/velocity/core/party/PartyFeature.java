package net.retromc.velocity.core.party;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.PartyChat;
import net.retromc.core.api.event.PartyDisband;
import net.retromc.core.api.event.PartyWarp;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.api.social.PartyOutcome;
import net.retromc.core.api.social.PartyResult;
import net.retromc.core.api.social.PartyService;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.PublicFilter;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import net.retromc.velocity.core.spam.ProxyChat;
import org.incendo.cloud.Command;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Party (Spec 01 §3.3 #22–39, §6.3 Nr. 6/7, §6.5): {@code /party} mit Kurzbefehlen {@code /pi /pa /pl /pk /ppm /pj
 * /pp /p}, {@code /partytoggle}; Einladungen 60 s ({@code PARTY_INVITE} an den Proxy des Ziels), Partychat
 * netzwerkweit ({@code PARTY_CHAT}), Nachziehen beim Betreten eines Spielservers durch den Leiter ({@code PARTY_WARP}),
 * Offline-Karenz 300 s, Auflösen leerer Partys nach 60 s, AFK-Meldungen. Die Regeln setzt der {@link PartyService}
 * um, das Partymenü §5.1 öffnet paper-core.
 */
public final class PartyFeature implements CoreFeature<VelocityCore> {

    public static final CommandName PARTY = CommandName.of("party");
    public static final CommandName PI = CommandName.of("pi");
    public static final CommandName PA = CommandName.of("pa");
    public static final CommandName PL = CommandName.of("pl");
    public static final CommandName PK = CommandName.of("pk");
    public static final CommandName PPM = CommandName.of("ppm");
    public static final CommandName PJ = CommandName.of("pj");
    public static final CommandName PP = CommandName.of("pp");
    public static final CommandName P = CommandName.of("p");
    public static final CommandName PARTYTOGGLE = CommandName.of("partytoggle");
    public static final List<CommandName> COMMANDS = List.of(PARTY, PI, PA, PL, PK, PPM, PJ, PP, P, PARTYTOGGLE);

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;
    private @Nullable ProxyChat chat;

    @Override
    public String name() {
        return "Party";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        chat = new ProxyChat(helper);
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var party = PARTY.builder(manager).senderType(Player.class);
        // Ohne Argument: Hilfe 1 (Spec 01 #22); das Partymenü öffnet das Profil-Item der Lobby (§4.5).
        manager.command(party.handler(ctx -> helper.send(ctx.sender(), "help.party.1")));
        manager.command(party.literal("2").handler(ctx -> helper.send(ctx.sender(), "help.party.2")));
        registerPlayerCommand(manager, party.literal("invite"), PI, this::invite, helper);
        registerPlayerCommand(manager, party.literal("accept"), PA, this::accept, helper);
        manager.command(party.literal("deny").required("player", stringParser())
                .handler(ctx -> deny(ctx.sender(), ctx.get("player"))));
        manager.command(party.literal("leave").handler(ctx -> leave(ctx.sender())));
        manager.command(PL.builder(manager).senderType(Player.class).handler(ctx -> leave(ctx.sender())));
        registerPlayerCommand(manager, party.literal("kick"), PK, this::kick, helper);
        registerPlayerCommand(manager, party.literal("promote"), PPM, this::promote, helper);
        manager.command(party.literal("jump").handler(ctx -> jump(ctx.sender())));
        manager.command(PJ.builder(manager).senderType(Player.class).handler(ctx -> jump(ctx.sender())));
        manager.command(party.literal("pull").handler(ctx -> pull(ctx.sender())));
        manager.command(party.literal("chat").required("text", greedyStringParser())
                .handler(ctx -> partyChat(ctx.sender(), ctx.get("text"))));
        manager.command(P.builder(manager).senderType(Player.class).required("text", greedyStringParser())
                .handler(ctx -> partyChat(ctx.sender(), ctx.get("text"))));
        manager.command(P.builder(manager).senderType(Player.class)
                .handler(ctx -> helper.send(ctx.sender(), "help.party.1")));
        manager.command(party.literal("list").handler(ctx -> list(ctx.sender())));
        manager.command(party.literal("public").handler(ctx -> togglePublic(ctx.sender())));
        manager.command(PP.builder(manager).senderType(Player.class).handler(ctx -> togglePublic(ctx.sender())));
        manager.command(party.literal("publicfilter").required("filter", stringParser())
                .handler(ctx -> filter(ctx.sender(), ctx.get("filter"))));
        manager.command(party.literal("requests").required("value", stringParser())
                .handler(ctx -> requests(ctx.sender(), ctx.get("value"))));
        manager.command(party.literal("join").required("player", stringParser(), helper.players())
                .handler(ctx -> join(ctx.sender(), ctx.get("player"))));
        manager.command(party.literal("random").handler(ctx -> random(ctx.sender())));
        manager.command(party.literal("disband").handler(ctx -> disband(ctx.sender())));
        manager.command(PARTYTOGGLE.builder(manager).senderType(Player.class).optional("value", stringParser())
                .handler(ctx -> toggle(ctx.sender(), ctx.<String>optional("value").orElse(null))));
        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.PARTY_INVITE, envelope -> onInvite(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.PARTY_CHAT, envelope -> onChat(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.PARTY_DISBAND, envelope -> onDisband(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.PARTY_WARP, envelope -> onWarp(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.AFK_CHANGED, envelope -> onAfk(envelope.d())).join());
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    /** {@code /party <sub> <Spieler>} und Kurzbefehl {@code /<kurz> <Spieler>}. */
    private void registerPlayerCommand(VelocityCommandManager<CommandSource> manager,
            Command.Builder<Player> sub, CommandName shortcut, PlayerAction action, Social helper) {
        manager.command(sub.required("player", stringParser(), helper.players())
                .handler(ctx -> action.run(ctx.sender(), ctx.get("player"))));
        manager.command(shortcut.builder(manager).senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .handler(ctx -> action.run(ctx.sender(), ctx.get("player"))));
    }

    /** Befehl mit Spielerargument. */
    @FunctionalInterface
    private interface PlayerAction {
        void run(Player sender, String name);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
        chat = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Party ist nicht aktiv");
        }
        return current;
    }

    private PartyService parties() {
        return social().get(PartyService.class);
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

    /** Text mit {@code <name>} in Rangfarbe an einen Spieler. */
    private void sendNamed(Player receiver, String key, UUID named, String name) {
        Social helper = social();
        helper.then(receiver, helper.coloredName(named, name), colored -> helper.send(receiver, key,
                Placeholder.component("name", colored)));
    }

    /** Text mit {@code <name>} an alle Mitglieder auf diesem Proxy (und weitere). */
    private void toMembers(@Nullable Party party, List<UUID> extra, String key, UUID named) {
        Social helper = social();
        List<UUID> targets = new ArrayList<>(party == null ? List.of() : party.memberIds());
        extra.stream().filter(uuid -> !targets.contains(uuid)).forEach(targets::add);
        helper.log(helper.nameOf(named).thenCompose(name -> helper.coloredName(named, name)).thenAccept(colored ->
                targets.forEach(uuid -> helper.send(uuid, key, Placeholder.component("name", colored)))),
                "Party-Meldung");
    }

    /** Fehlertexte (Spec 01 §3.3 Ausgaben). */
    public static String errorKey(PartyOutcome outcome) {
        return switch (outcome) {
            case NOT_IN_PARTY -> "party.err.notinparty";
            case NOT_LEADER -> "party.err.notleader";
            case FULL -> "party.err.full";
            case ALREADY_MEMBER -> "party.err.already.member";
            case ALREADY_INVITED -> "party.err.already.invited";
            case INVITE_BLOCKED -> "party.err.invite.denied";
            case NO_INVITE -> "party.err.noinvite";
            case IN_PARTY -> "party.err.inparty";
            case NOT_PUBLIC -> "party.err.notpublic";
            case FILTERED -> "party.err.filter";
            case NO_PUBLIC_PARTY -> "party.err.nopublic";
            case RANK_REQUIRED -> "error.rank.required";
            case TARGET_OFFLINE -> "error.player.offline";
            case SELF -> "party.err.self";
            case NOT_MEMBER -> "party.err.notmember";
            case NO_REQUEST -> "party.err.norequest";
            case INVITED, JOINED, REQUEST_SENT, DENIED, LEFT, KICKED, PROMOTED, DISBANDED, UPDATED ->
                    throw new IllegalArgumentException("Kein Fehler: " + outcome);
        };
    }

    private void error(Player sender, PartyResult result, @Nullable PlayerRef target) {
        Social helper = social();
        UUID named = target == null ? sender.getUniqueId() : target.uuid();
        String name = target == null ? sender.getUsername() : target.name();
        int max = result.party() == null ? 0 : result.party().maxSize();
        helper.then(sender, helper.coloredName(named, name), colored -> helper.send(sender,
                errorKey(result.outcome()), Placeholder.component("name", colored), Social.player(name),
                Social.unparsed("max", max), Placeholder.component("rank", Social.coloredName(
                        RankInfo.of(Rank.PREMIUM), Rank.PREMIUM.displayName()))));
    }

    /** Leere Party nach 60 s auflösen (Spec 01 §6.5 Nr. 7). */
    private void scheduleAbandonCheck(@Nullable Party party, Duration delay) {
        Social helper = social;
        if (helper == null || party == null || party.size() > 1) {
            return;
        }
        helper.core().proxy().getScheduler().buildTask(helper.core().plugin(), () -> {
            Social current = social;
            if (current != null) {
                current.log(current.get(PartyService.class).disbandIfAbandoned(party.id()), "Leere Party prüfen");
            }
        }).delay(delay.toMillis() + 1000, TimeUnit.MILLISECONDS).schedule();
    }

    // ------------------------------------------------------------------ Einladen

    void invite(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().invite(sender.getUniqueId(), target.uuid()),
                result -> {
                    if (result.outcome() != PartyOutcome.INVITED) {
                        error(sender, result, target);
                        return;
                    }
                    sendNamed(sender, "party.invite.sent", target.uuid(), target.name());
                    helper.core().proxy().getScheduler().buildTask(helper.core().plugin(),
                            () -> expireInvite(sender.getUniqueId(), target, result.party()))
                            .delay(PartyService.INVITE_TTL.toMillis() + 500, TimeUnit.MILLISECONDS).schedule();
                }));
    }

    private void expireInvite(UUID inviter, PlayerRef target, @Nullable Party party) {
        Social helper = social;
        if (helper == null || party == null) {
            return;
        }
        helper.log(helper.get(PartyService.class).party(party.id()).thenAccept(current -> {
            if (current.isPresent() && !current.get().isMember(target.uuid())) {
                helper.online(inviter).ifPresent(player -> sendNamed(player, "party.invite.expired", target.uuid(),
                        target.name()));
            }
            scheduleAbandonCheck(current.orElse(null), Duration.ZERO);
        }), "Einladung ablaufen lassen");
    }

    /** {@code PARTY_INVITE}: Einladung mit Klickzeile beim Ziel (Proxy des Ziels). */
    void onInvite(PartyInvite invite) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.online(invite.target()).ifPresent(target -> helper.log(helper.nameOf(invite.inviter())
                .thenAccept(name -> {
                    sendNamed(target, "party.invite.received", invite.inviter(), name);
                    target.sendMessage(helper.clickLine(target, "prefix.party", "click.accept",
                            "/party accept " + name, "click.deny", "/party deny " + name));
                }), "Partyeinladung zustellen"));
    }

    /** {@code /party accept <Spieler>}: Einladung annehmen oder als Leiter eine Beitrittsanfrage. */
    void accept(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().joinRequests(sender.getUniqueId()),
                requests -> {
                    CompletableFuture<PartyResult> done = requests.contains(target.uuid())
                            ? parties().acceptRequest(sender.getUniqueId(), target.uuid())
                            : parties().accept(sender.getUniqueId(), target.uuid());
                    UUID joiner = requests.contains(target.uuid()) ? target.uuid() : sender.getUniqueId();
                    helper.then(sender, done, result -> {
                        if (result.outcome() == PartyOutcome.JOINED) {
                            toMembers(result.party(), List.of(), "party.joined", joiner);
                        } else {
                            error(sender, result, target);
                        }
                    });
                }));
    }

    void deny(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().deny(sender.getUniqueId(), target.uuid()),
                result -> {
                    if (result.outcome() != PartyOutcome.DENIED) {
                        error(sender, result, target);
                        return;
                    }
                    helper.online(target.uuid()).ifPresent(inviter -> sendNamed(inviter, "party.invite.denied",
                            sender.getUniqueId(), sender.getUsername()));
                }));
    }

    // ------------------------------------------------------------------ Verlassen, Leitung

    void leave(Player sender) {
        Social helper = social();
        helper.then(sender, parties().partyOf(sender.getUniqueId()).thenCombine(parties().leave(sender.getUniqueId()),
                Change::new), change -> afterRemoval(sender, change, sender.getUniqueId(), "party.left"));
    }

    /** Stand vor und nach einer Änderung. */
    private record Change(Optional<Party> before, PartyResult result) {
    }

    private void afterRemoval(Player sender, Change change, UUID removed, String key) {
        PartyResult result = change.result();
        if (result.outcome() != PartyOutcome.LEFT && result.outcome() != PartyOutcome.KICKED) {
            error(sender, result, null);
            return;
        }
        Party after = result.party();
        toMembers(after, List.of(removed), key, removed);
        if (result.outcome() == PartyOutcome.KICKED) {
            social().send(removed, "party.kicked.you");
        }
        if (after != null && change.before().isPresent() && !change.before().get().leader().equals(after.leader())) {
            toMembers(after, List.of(), "party.leader.new", after.leader());
        }
        scheduleAbandonCheck(after, PartyService.ABANDONED_AFTER);
    }

    void kick(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().partyOf(sender.getUniqueId())
                .thenCombine(parties().kick(sender.getUniqueId(), target.uuid()), Change::new), change -> {
                    if (change.result().outcome() == PartyOutcome.KICKED) {
                        afterRemoval(sender, change, target.uuid(), "party.kicked");
                    } else {
                        error(sender, change.result(), target);
                    }
                }));
    }

    void promote(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().promote(sender.getUniqueId(),
                target.uuid()), result -> {
                    if (result.outcome() == PartyOutcome.PROMOTED) {
                        toMembers(result.party(), List.of(), "party.leader.new", target.uuid());
                    } else {
                        error(sender, result, target);
                    }
                }));
    }

    void disband(Player sender) {
        Social helper = social();
        helper.then(sender, parties().disband(sender.getUniqueId()), result -> {
            if (result.outcome() != PartyOutcome.DISBANDED) {
                error(sender, result, null);
            }
        });
    }

    void onDisband(PartyDisband disband) {
        Social helper = social;
        if (helper != null) {
            disband.members().forEach(uuid -> helper.send(uuid, "party.disbanded"));
        }
    }

    // ------------------------------------------------------------------ Server

    /** Server ist eine Lobby oder eine Wartelobby (keine laufende Runde). */
    private boolean openForParty(String server) {
        Social helper = social();
        if (helper.category(server) != ServerCategory.GAME) {
            return true;
        }
        return helper.registryInfo(server).map(ServerInfo::state)
                .map(state -> state == ServerState.WAITING || state == ServerState.STARTING).orElse(true);
    }

    private boolean joinable(String server) {
        return social().registryInfo(server).map(ServerInfo::joinable).orElse(true);
    }

    void jump(Player sender) {
        Social helper = social();
        helper.then(sender, parties().partyOf(sender.getUniqueId()), party -> {
            if (party.isEmpty()) {
                helper.send(sender, "party.err.notinparty");
                return;
            }
            UUID leader = party.get().leader();
            CompletableFuture<Optional<String>> server = helper.online(leader).isPresent()
                    ? CompletableFuture.completedFuture(Social.serverName(helper.online(leader).get()))
                    : helper.get(PresenceService.class).serverOf(leader);
            helper.then(sender, server, target -> {
                Optional<String> own = Social.serverName(sender);
                if (target.isEmpty() || !joinable(target.get())) {
                    helper.send(sender, "party.err.jump");
                } else if (own.equals(target)) {
                    helper.send(sender, "party.err.leader.same");
                } else {
                    helper.send(sender, "party.jump.ok");
                    connect(sender, target.get());
                }
            });
        });
    }

    private void connect(Player player, String server) {
        social().core().proxy().getServer(server)
                .ifPresent(target -> player.createConnectionRequest(target).fireAndForget());
    }

    void pull(Player sender) {
        Social helper = social();
        helper.then(sender, parties().partyOf(sender.getUniqueId()), party -> {
            if (party.isEmpty()) {
                helper.send(sender, "party.err.notinparty");
                return;
            }
            if (!party.get().isLeader(sender.getUniqueId())) {
                helper.send(sender, "party.err.notleader");
                return;
            }
            Optional<String> server = Social.serverName(sender);
            if (server.isEmpty() || !openForParty(server.get())) {
                helper.send(sender, "party.err.pull");
                return;
            }
            pullMembers(party.get(), server.get(), sender);
        });
    }

    /** Mitglieder auf den Server holen; Spieler in laufender Runde werden übersprungen (§6.5 Nr. 4). */
    private void pullMembers(Party party, String server, @Nullable Player leader) {
        Social helper = social();
        for (UUID member : party.memberIds()) {
            if (member.equals(party.leader())) {
                continue;
            }
            helper.online(member).ifPresent(player -> {
                Optional<String> own = Social.serverName(player);
                if (own.isPresent() && own.get().equals(server)) {
                    return;
                }
                if (own.isPresent() && !openForParty(own.get())) {
                    if (leader != null) {
                        sendNamed(leader, "party.pull.skipped", member, player.getUsername());
                    }
                    return;
                }
                helper.send(player, "party.pull");
                connect(player, server);
            });
        }
    }

    /** Leiter betritt einen Spielserver: Party nachziehen (§6.5 Nr. 3) und {@code PARTY_WARP} senden. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        if (event.getPreviousServer().isEmpty()) {
            helper.log(helper.get(PartyService.class).rejoin(player.getUniqueId()).thenAccept(party -> party
                    .ifPresent(value -> helper.log(helper.nameOf(value.leader()).thenAccept(name -> sendNamed(player,
                            "party.rejoined", value.leader(), name)), "Party-Rückkehr"))), "Party-Karenz");
            return;
        }
        if (helper.category(server) != ServerCategory.GAME) {
            return;
        }
        helper.log(helper.get(PartyService.class).partyOf(player.getUniqueId()).thenAccept(party -> {
            if (party.isPresent() && party.get().isLeader(player.getUniqueId()) && party.get().size() > 1) {
                helper.log(helper.get(MessageBus.class).publish(MessageType.PARTY_WARP,
                        new PartyWarp(party.get().id(), server)), "PARTY_WARP senden");
            }
        }), "Party nachziehen");
    }

    /** {@code PARTY_WARP}: Mitglieder auf diesem Proxy folgen dem Leiter (nicht aus laufenden Runden). */
    void onWarp(PartyWarp warp) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Optional<RegisteredServer> target = helper.core().proxy().getServer(warp.server());
        if (target.isEmpty()) {
            return;
        }
        helper.log(helper.get(PartyService.class).party(warp.partyId()).thenAccept(party -> party.ifPresent(value ->
                pullMembers(value, warp.server(), helper.online(value.leader()).orElse(null)))), "Party-Warp");
    }

    /** Offline-Karenz 300 s, danach entfernen (Spec 01 §6.3 Nr. 7). */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        UUID player = event.getPlayer().getUniqueId();
        helper.log(helper.get(PartyService.class).markOffline(player), "Party-Karenz setzen");
        helper.core().proxy().getScheduler().buildTask(helper.core().plugin(), () -> expire(player))
                .delay(PartyService.OFFLINE_GRACE.toSeconds(), TimeUnit.SECONDS).schedule();
    }

    private void expire(UUID player) {
        Social helper = social;
        if (helper == null || helper.online(player).isPresent()) {
            return;
        }
        PartyService service = helper.get(PartyService.class);
        helper.log(helper.get(PresenceService.class).presence(player).thenCompose(presence -> presence.isPresent()
                ? CompletableFuture.completedFuture(null)
                : service.partyOf(player).thenCombine(service.expireOffline(player), Change::new)
                        .thenAccept(change -> {
                            if (change.result().outcome() == PartyOutcome.LEFT) {
                                Party after = change.result().party();
                                toMembers(after, List.of(), "party.left", player);
                                if (after != null && change.before().isPresent()
                                        && !change.before().get().leader().equals(after.leader())) {
                                    toMembers(after, List.of(), "party.leader.new", after.leader());
                                }
                                scheduleAbandonCheck(after, PartyService.ABANDONED_AFTER);
                            }
                        })), "Party-Karenz ablaufen lassen");
    }

    // ------------------------------------------------------------------ Chat, Liste

    void partyChat(Player sender, String text) {
        Social helper = social();
        ProxyChat guard = chat;
        if (guard == null) {
            return;
        }
        helper.then(sender, parties().partyOf(sender.getUniqueId()), party -> {
            if (party.isEmpty()) {
                helper.send(sender, "party.err.notinparty");
                return;
            }
            helper.then(sender, guard.check(sender, text, ChatContext.Source.PARTY_CHAT), checked -> checked
                    .ifPresent(value -> helper.log(helper.get(MessageBus.class).publish(MessageType.PARTY_CHAT,
                            new PartyChat(sender.getUniqueId(), party.get().memberIds(), value)), "Partychat")));
        });
    }

    void onChat(PartyChat message) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.nameOf(message.from()).thenCompose(name -> helper.coloredName(message.from(), name))
                .thenAccept(name -> message.to().forEach(uuid -> helper.send(uuid, "party.chat",
                        Placeholder.component("name", name), Placeholder.unparsed("text", message.text())))),
                "Partychat zustellen");
    }

    void list(Player sender) {
        Social helper = social();
        helper.then(sender, parties().partyOf(sender.getUniqueId()), party -> {
            if (party.isEmpty()) {
                helper.send(sender, "party.err.notinparty");
                return;
            }
            List<CompletableFuture<Component>> names = party.get().displayOrder().stream()
                    .map(uuid -> helper.nameOf(uuid).thenCompose(name -> helper.coloredName(uuid, name))).toList();
            helper.then(sender, CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)), ignored -> {
                List<Component> colored = names.stream().map(CompletableFuture::join).toList();
                helper.send(sender, "party.list.header", Placeholder.component("name", colored.getFirst()),
                        Social.unparsed("n", party.get().size()));
                helper.send(sender, "party.list.names", Formatter.joining("names", colored));
            });
        });
    }

    // ------------------------------------------------------------------ Öffentlich

    void togglePublic(Player sender) {
        Social helper = social();
        helper.then(sender, parties().partyOf(sender.getUniqueId()).thenCompose(party -> party.isEmpty()
                ? CompletableFuture.completedFuture(PartyResult.of(PartyOutcome.NOT_IN_PARTY))
                : parties().setPublic(sender.getUniqueId(), !party.get().publicParty())), result -> {
                    if (result.outcome() == PartyOutcome.UPDATED && result.party() != null) {
                        helper.send(sender, result.party().publicParty() ? "party.public.on" : "party.public.off");
                    } else {
                        error(sender, result, null);
                    }
                });
    }

    void filter(Player sender, String value) {
        Social helper = social();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("alle") && !lower.equals("premium")) {
            helper.send(sender, "error.command.usage", Social.unparsed("syntax", "party publicfilter <alle|premium>"));
            return;
        }
        PublicFilter filter = lower.equals("premium") ? PublicFilter.PREMIUM : PublicFilter.ALL;
        helper.then(sender, parties().setPublicFilter(sender.getUniqueId(), filter), result -> {
            if (result.outcome() == PartyOutcome.UPDATED) {
                helper.send(sender, "party.public.filter", Formatter.booleanChoice("premium",
                        filter == PublicFilter.PREMIUM));
            } else {
                error(sender, result, null);
            }
        });
    }

    void requests(Player sender, String value) {
        Social helper = social();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("an") && !lower.equals("aus")) {
            helper.send(sender, "error.command.usage", Social.unparsed("syntax", "party requests <an|aus>"));
            return;
        }
        boolean enabled = lower.equals("an");
        helper.then(sender, parties().setRequireRequest(sender.getUniqueId(), enabled), result -> {
            if (result.outcome() == PartyOutcome.UPDATED) {
                helper.send(sender, "party.public.requests", Formatter.booleanChoice("enabled", enabled));
            } else {
                error(sender, result, null);
            }
        });
    }

    void join(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, parties().join(sender.getUniqueId(), target.uuid()),
                result -> joined(sender, result, target)));
    }

    void random(Player sender) {
        social().then(sender, parties().joinRandom(sender.getUniqueId()), result -> joined(sender, result, null));
    }

    private void joined(Player sender, PartyResult result, @Nullable PlayerRef target) {
        Social helper = social();
        switch (result.outcome()) {
            case JOINED -> toMembers(result.party(), List.of(), "party.joined", sender.getUniqueId());
            case REQUEST_SENT -> {
                Party party = result.party();
                if (target != null) {
                    sendNamed(sender, "party.request.sent", target.uuid(), target.name());
                }
                if (party != null) {
                    helper.online(party.leader()).ifPresent(leader -> {
                        sendNamed(leader, "party.request.received", sender.getUniqueId(), sender.getUsername());
                        leader.sendMessage(helper.clickLine(leader, "prefix.party", "click.accept",
                                "/party accept " + sender.getUsername()));
                    });
                }
            }
            default -> error(sender, result, target);
        }
    }

    void toggle(Player sender, @Nullable String value) {
        Social helper = social();
        SettingsService settings = helper.get(SettingsService.class);
        CompletableFuture<PrivacySettings> changed;
        if (value == null) {
            changed = settings.cycle(sender.getUniqueId(), Setting.PARTY_FROM);
        } else {
            int target = switch (value.toLowerCase(Locale.ROOT)) {
                case "alle" -> 2;
                case "freunde" -> 1;
                case "aus" -> 0;
                default -> -1;
            };
            if (target < 0) {
                helper.send(sender, "error.command.usage", Social.unparsed("syntax",
                        "partytoggle [alle|freunde|aus]"));
                return;
            }
            changed = settings.set(sender.getUniqueId(), Setting.PARTY_FROM, target);
        }
        helper.then(sender, changed, current -> helper.send(sender, switch (current.partyFrom()) {
            case 2 -> "party.toggle.all";
            case 1 -> "party.toggle.friends";
            default -> "party.toggle.off";
        }));
    }

    /** {@code AFK_CHANGED}: Meldung an die anderen Mitglieder (Spec 01 §6.5 Nr. 5, A16). */
    void onAfk(AfkChanged change) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.get(PartyService.class).partyOf(change.uuid()).thenAccept(party -> party.ifPresent(value ->
                helper.log(helper.nameOf(change.uuid()).thenCompose(name -> helper.coloredName(change.uuid(), name))
                        .thenAccept(colored -> afkRecipients(value, change.uuid())
                                .forEach(uuid -> helper.send(uuid, change.afk() ? "party.afk.on" : "party.afk.off",
                                        Placeholder.component("name", colored)))), "AFK-Meldung"))),
                "AFK an Party");
    }

    /** Empfänger einer AFK-Meldung: alle Mitglieder außer dem Spieler selbst (A16). */
    public static List<UUID> afkRecipients(Party party, UUID player) {
        return party.memberIds().stream().filter(uuid -> !uuid.equals(player)).toList();
    }
}
