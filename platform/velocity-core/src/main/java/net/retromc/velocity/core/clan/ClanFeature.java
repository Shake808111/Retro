package net.retromc.velocity.core.clan;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.event.ClanChat;
import net.retromc.core.api.event.ClanMemberStatus;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.social.Clan;
import net.retromc.core.api.social.ClanMember;
import net.retromc.core.api.social.ClanOutcome;
import net.retromc.core.api.social.ClanResult;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.ClanRules;
import net.retromc.core.api.social.ClanService;
import net.retromc.core.api.social.ClanWarStats;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.bridge.ServerBridge;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import net.retromc.velocity.core.spam.ProxyChat;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Clans auf dem Proxy (Spec 01 §3.4 #40–62, §6.6): Befehle {@code /clan …}, {@code /cc}, {@code /clc},
 * {@code /clanlvl}, {@code /clanstatsreset}; Clan-Chat und Online-/Offline-Meldungen über {@code net:chat} bzw.
 * {@code net:social}; Inaktivitäts-Job täglich 04:00 (Europe/Berlin, 60 Tage). Die Regeln setzt der
 * {@link ClanService} um, die Clan-GUI öffnet paper-core ({@code OPEN_GUI clan.settings}).
 */
public final class ClanFeature implements CoreFeature<VelocityCore> {

    public static final CommandName CLAN = CommandName.of("clan");
    public static final CommandName CC = CommandName.of("cc");
    public static final CommandName CLC = CommandName.of("clc");
    public static final CommandName CLANLVL = CommandName.of("clanlvl");
    public static final CommandName CLANSTATSRESET = CommandName.of("clanstatsreset");
    public static final List<CommandName> COMMANDS = List.of(CLAN, CC, CLC, CLANLVL, CLANSTATSRESET);

    /** GUI-ID der Clan-Einstellungen auf paper-core (§5.5). */
    public static final String GUI_SETTINGS = "clan.settings";

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(BERLIN);
    private static final LocalTime JOB_TIME = LocalTime.of(4, 0);

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;
    private @Nullable ProxyChat chat;
    private @Nullable ScheduledTask job;

    @Override
    public String name() {
        return "Clans";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        chat = new ProxyChat(helper);
        registerCommands(core.services().get(CommandService.class).manager(), helper);
        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.CLAN_CHAT, envelope -> deliverChat(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.CLAN_ONLINE, envelope -> deliverStatus(envelope.d(), true))
                .join());
        subscriptions.add(bus.subscribe(MessageType.CLAN_OFFLINE, envelope -> deliverStatus(envelope.d(), false))
                .join());
        core.proxy().getEventManager().register(core.plugin(), this);
        job = core.proxy().getScheduler().buildTask(core.plugin(), this::deleteInactive)
                .delay(untilNextRun(Instant.now()).toMillis(), TimeUnit.MILLISECONDS)
                .repeat(1, TimeUnit.DAYS).schedule();
    }

    @Override
    public void disable(VelocityCore core) {
        ScheduledTask task = job;
        if (task != null) {
            task.cancel();
        }
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        job = null;
        social = null;
        chat = null;
    }

    private void registerCommands(VelocityCommandManager<CommandSource> manager, Social helper) {
        var clan = CLAN.builder(manager).senderType(Player.class);
        manager.command(clan.handler(ctx -> helper.send(ctx.sender(), "help.clan.1")));
        manager.command(clan.literal("2").handler(ctx -> helper.send(ctx.sender(), "help.clan.2")));
        manager.command(clan.literal("create").required("name", stringParser()).required("tag", stringParser())
                .handler(ctx -> create(ctx.sender(), ctx.get("name"), ctx.get("tag"))));
        manager.command(clan.literal("invite").required("player", stringParser(), helper.players())
                .handler(ctx -> invite(ctx.sender(), ctx.get("player"))));
        manager.command(clan.literal("join").required("clan", stringParser())
                .handler(ctx -> join(ctx.sender(), ctx.get("clan"))));
        manager.command(clan.literal("request").required("tag", stringParser())
                .handler(ctx -> request(ctx.sender(), ctx.get("tag"))));
        manager.command(clan.literal("accept").required("player", stringParser())
                .handler(ctx -> answerRequest(ctx.sender(), ctx.get("player"), true)));
        manager.command(clan.literal("deny").required("player", stringParser())
                .handler(ctx -> answerRequest(ctx.sender(), ctx.get("player"), false)));
        manager.command(clan.literal("leave").handler(ctx -> leave(ctx.sender())));
        manager.command(clan.literal("kick").required("player", stringParser())
                .handler(ctx -> kick(ctx.sender(), ctx.get("player"))));
        manager.command(clan.literal("promote").required("player", stringParser())
                .handler(ctx -> promote(ctx.sender(), ctx.get("player"), false)));
        manager.command(clan.literal("promote").required("player", stringParser()).literal("confirm")
                .handler(ctx -> promote(ctx.sender(), ctx.get("player"), true)));
        manager.command(clan.literal("demote").required("player", stringParser())
                .handler(ctx -> demote(ctx.sender(), ctx.get("player"))));
        manager.command(clan.literal("info").optional("clan", stringParser())
                .handler(ctx -> info(ctx.sender(), ctx.<String>optional("clan").orElse(null))));
        manager.command(clan.literal("list").handler(ctx -> list(ctx.sender())));
        manager.command(clan.literal("rename").required("name", stringParser()).required("tag", stringParser())
                .handler(ctx -> rename(ctx.sender(), ctx.get("name"), ctx.get("tag"))));
        manager.command(clan.literal("delete").handler(ctx -> {
            helper.send(ctx.sender(), "clan.delete.confirm");
            ctx.sender().sendMessage(helper.clickLine(ctx.sender(), "prefix.clan", "click.confirm",
                    "/clan delete confirm"));
        }));
        manager.command(clan.literal("delete").literal("confirm").handler(ctx -> delete(ctx.sender())));
        manager.command(clan.literal("stats").optional("mode", stringParser())
                .handler(ctx -> stats(ctx.sender(), ctx.<String>optional("mode").orElse(null))));
        manager.command(clan.literal("togglestats").required("mode", stringParser())
                .handler(ctx -> toggleStats(ctx.sender(), ctx.get("mode"))));
        manager.command(clan.literal("cwstats").required("clan", stringParser()).optional("mode", stringParser())
                .handler(ctx -> warStats(ctx.sender(), ctx.get("clan"), ctx.<String>optional("mode").orElse(null))));
        manager.command(clan.literal("settings").handler(ctx -> settings(ctx.sender())));
        manager.command(clan.literal("chat").required("text", greedyStringParser())
                .handler(ctx -> clanChat(ctx.sender(), ctx.get("text"), false)));
        manager.command(CC.builder(manager).senderType(Player.class).required("text", greedyStringParser())
                .handler(ctx -> clanChat(ctx.sender(), ctx.get("text"), false)));
        manager.command(CLC.builder(manager).senderType(Player.class).required("text", greedyStringParser())
                .handler(ctx -> clanChat(ctx.sender(), ctx.get("text"), true)));
        manager.command(CLANLVL.builder(manager).senderType(Player.class).handler(ctx -> level(ctx.sender())));
        manager.command(CLANSTATSRESET.builder(manager).senderType(Player.class).required("mode", stringParser())
                .handler(ctx -> confirmReset(ctx.sender(), ctx.get("mode"))));
        manager.command(CLANSTATSRESET.builder(manager).senderType(Player.class).required("mode", stringParser())
                .literal("confirm").handler(ctx -> reset(ctx.sender(), ctx.get("mode"))));
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Clans sind nicht aktiv");
        }
        return current;
    }

    private ClanService clans() {
        return social().get(ClanService.class);
    }

    // ------------------------------------------------------------------ Ausgaben

    /** Fehlertext mit allen möglichen Platzhaltern. */
    private void error(CommandSource source, ClanResult result, String player, String clanOrTag) {
        Social helper = social();
        Clan clan = result.clan();
        TagResolver placeholders = TagResolver.resolver(Social.player(player),
                Social.unparsed("clan", clan == null ? clanOrTag : clan.name()),
                Social.unparsed("tag", clan == null ? clanOrTag : clan.tag()),
                Social.unparsed("max", ClanRules.BASE_MEMBERS));
        if (result.outcome() == ClanOutcome.FULL && clan != null) {
            helper.then(source, clans().maxMembers(clan.id()), max -> helper.send(source, "clan.err.full",
                    Social.unparsed("max", max)));
            return;
        }
        helper.send(source, ClanTexts.error(result.outcome()), placeholders);
    }

    /** Text an alle Mitglieder des Clans auf diesem Proxy (und optional an weitere Spieler). */
    private void broadcast(long clanId, String key, List<UUID> extra, TagResolver... placeholders) {
        Social helper = social();
        helper.log(clans().members(clanId).thenAccept(members -> {
            List<UUID> targets = new ArrayList<>(members.stream().map(ClanMember::uuid).toList());
            extra.stream().filter(uuid -> !targets.contains(uuid)).forEach(targets::add);
            targets.forEach(uuid -> helper.send(uuid, key, placeholders));
        }), "Clan-Meldung");
    }

    private CompletableFuture<Component> colored(PlayerRef player) {
        return social().coloredName(player.uuid(), player.name());
    }

    /** Spieler auflösen, dann {@code action}; unbekannt → {@code error.player.unknown}. */
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

    // ------------------------------------------------------------------ Gründen, Einladen, Beitreten

    void create(Player sender, String name, String tag) {
        Social helper = social();
        helper.then(sender, clans().create(sender.getUniqueId(), name, tag), result -> {
            if (result.outcome() == ClanOutcome.CREATED) {
                helper.send(sender, "clan.created", Social.unparsed("clan", name), Social.unparsed("tag", tag));
            } else {
                error(sender, result, sender.getUsername(), result.outcome() == ClanOutcome.TAG_TAKEN ? tag : name);
            }
        });
    }

    void invite(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, clans().invite(sender.getUniqueId(), target.uuid()),
                result -> {
                    if (result.outcome() != ClanOutcome.INVITED || result.clan() == null) {
                        error(sender, result, target.name(), "");
                        return;
                    }
                    Clan clan = result.clan();
                    helper.then(sender, colored(target), coloredName -> helper.send(sender, "clan.invite.sent",
                            Placeholder.component("name", coloredName)));
                    helper.online(target.uuid()).ifPresent(invited -> {
                        helper.send(invited, "clan.invite.received", Social.unparsed("clan", clan.name()));
                        invited.sendMessage(helper.clickLine(invited, "prefix.clan", "click.accept",
                                "/clan join " + clan.name()));
                    });
                }));
    }

    void join(Player sender, String clanName) {
        Social helper = social();
        helper.then(sender, clans().join(sender.getUniqueId(), clanName), result -> {
            if (result.outcome() != ClanOutcome.JOINED || result.clan() == null) {
                error(sender, result, sender.getUsername(), clanName);
                return;
            }
            announceJoin(result.clan().id(), new PlayerRef(sender.getUniqueId(), sender.getUsername()));
        });
    }

    private void announceJoin(long clanId, PlayerRef player) {
        social().log(colored(player).thenAccept(name -> broadcast(clanId, "clan.joined", List.of(),
                Placeholder.component("name", name))), "Clan-Beitritt melden");
    }

    void request(Player sender, String tag) {
        Social helper = social();
        helper.then(sender, clans().request(sender.getUniqueId(), tag), result -> {
            if (result.outcome() != ClanOutcome.REQUEST_SENT || result.clan() == null) {
                error(sender, result, sender.getUsername(), tag);
                return;
            }
            Clan clan = result.clan();
            helper.send(sender, "clan.request.sent", Social.unparsed("clan", clan.name()));
            helper.then(sender, clans().members(clan.id())
                    .thenCombine(helper.coloredName(sender.getUniqueId(), sender.getUsername()), Leaders::new),
                    leaders -> leaders.members().stream().filter(member -> member.role().canInvite())
                            .forEach(member -> helper.online(member.uuid()).ifPresent(lead -> {
                                helper.send(lead, "clan.request.received",
                                        Placeholder.component("name", leaders.name()));
                                lead.sendMessage(helper.clickLine(lead, "prefix.clan", "click.accept",
                                        "/clan accept " + sender.getUsername(), "click.deny",
                                        "/clan deny " + sender.getUsername()));
                            })));
        });
    }

    /** Mitglieder und farbiger Name des Anfragenden. */
    private record Leaders(List<ClanMember> members, Component name) {
    }

    void answerRequest(Player sender, String name, boolean accept) {
        Social helper = social();
        withPlayer(sender, name, target -> {
            CompletableFuture<ClanResult> done = accept
                    ? clans().acceptRequest(sender.getUniqueId(), target.uuid())
                    : clans().denyRequest(sender.getUniqueId(), target.uuid());
            helper.then(sender, done, result -> {
                if (result.outcome() == ClanOutcome.JOINED && result.clan() != null) {
                    announceJoin(result.clan().id(), target);
                } else if (result.outcome() == ClanOutcome.REQUEST_DENIED) {
                    helper.then(sender, colored(target), colored -> helper.send(sender, "clan.request.denied",
                            Placeholder.component("name", colored)));
                } else {
                    error(sender, result, target.name(), "");
                }
            });
        });
    }

    void leave(Player sender) {
        Social helper = social();
        helper.then(sender, clans().leave(sender.getUniqueId()), result -> {
            if (result.outcome() != ClanOutcome.LEFT || result.clan() == null) {
                error(sender, result, sender.getUsername(), "");
                return;
            }
            long clanId = result.clan().id();
            helper.then(sender, helper.coloredName(sender.getUniqueId(), sender.getUsername()), name -> broadcast(
                    clanId, "clan.left", List.of(sender.getUniqueId()), Placeholder.component("name", name)));
        });
    }

    void kick(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, clans().kick(sender.getUniqueId(), target.uuid()),
                result -> {
                    if (result.outcome() != ClanOutcome.KICKED || result.clan() == null) {
                        error(sender, result, target.name(), "");
                        return;
                    }
                    long clanId = result.clan().id();
                    helper.then(sender, colored(target), colored -> broadcast(clanId, "clan.kicked",
                            List.of(target.uuid()), Placeholder.component("name", colored)));
                }));
    }

    // ------------------------------------------------------------------ Rollen

    void promote(Player sender, String name, boolean confirmed) {
        Social helper = social();
        withPlayer(sender, name, target -> {
            CompletableFuture<ClanResult> done = confirmed
                    ? clans().transferLeadership(sender.getUniqueId(), target.uuid())
                    : clans().promote(sender.getUniqueId(), target.uuid());
            helper.then(sender, done, result -> {
                switch (result.outcome()) {
                    case CONFIRM_TRANSFER -> helper.then(sender, colored(target), colored -> {
                        helper.send(sender, "clan.transfer.confirm", Placeholder.component("name", colored));
                        sender.sendMessage(helper.clickLine(sender, "prefix.clan", "click.confirm",
                                "/clan promote " + target.name() + " confirm"));
                    });
                    case PROMOTED, LEADER_TRANSFERRED -> roleChanged(sender, result, target, "clan.promoted");
                    default -> error(sender, result, target.name(), "");
                }
            });
        });
    }

    void demote(Player sender, String name) {
        Social helper = social();
        withPlayer(sender, name, target -> helper.then(sender, clans().demote(sender.getUniqueId(), target.uuid()),
                result -> {
                    if (result.outcome() == ClanOutcome.DEMOTED) {
                        roleChanged(sender, result, target, "clan.demoted");
                    } else {
                        error(sender, result, target.name(), "");
                    }
                }));
    }

    private void roleChanged(Player sender, ClanResult result, PlayerRef target, String key) {
        Social helper = social();
        Clan clan = result.clan();
        if (clan == null) {
            return;
        }
        helper.then(sender, clans().role(target.uuid()).thenCombine(colored(target), (role, name) -> {
            Locale locale = helper.locale(sender);
            ClanRole value = role.orElse(ClanRole.MEMBER);
            return TagResolver.resolver(Placeholder.component("name", name),
                    Placeholder.component("role", helper.get(I18n.class)
                            .render(locale, value.i18nKey())));
        }), placeholders -> broadcast(clan.id(), key, List.of(), placeholders));
    }

    // ------------------------------------------------------------------ Info, Liste

    void info(Player sender, @Nullable String nameOrTag) {
        Social helper = social();
        CompletableFuture<Optional<Clan>> lookup = nameOrTag == null ? clans().clanOf(sender.getUniqueId())
                : clans().byName(nameOrTag).thenCompose(byName -> byName.isPresent()
                        ? CompletableFuture.completedFuture(byName) : clans().byTag(nameOrTag));
        helper.then(sender, lookup, found -> {
            if (found.isEmpty()) {
                helper.send(sender, nameOrTag == null ? "clan.err.notinclan" : "clan.err.unknown",
                        Social.unparsed("clan", nameOrTag == null ? "" : nameOrTag));
                return;
            }
            Clan clan = found.get();
            helper.then(sender, clans().members(clan.id()).thenCombine(clans().maxMembers(clan.id()), Info::new)
                    .thenCompose(info -> helper.nameOf(clan.owner())
                            .thenCompose(owner -> helper.coloredName(clan.owner(), owner))
                            .thenApply(owner -> new Info(info.members(), info.max(), owner))), info -> {
                                helper.send(sender, "clan.info.header", Social.unparsed("clan", clan.name()),
                                        Social.unparsed("tag", clan.tag()));
                                helper.send(sender, "clan.info.leader",
                                        Placeholder.component("name", info.leader()));
                                helper.send(sender, "clan.info.members", Social.unparsed("n", info.members().size()),
                                        Social.unparsed("max", info.max()));
                                helper.send(sender, "clan.info.level", Social.unparsed("lvl", clan.level().level()));
                                helper.send(sender, "clan.info.founded",
                                        Social.unparsed("date", DATE.format(clan.createdAt())));
                            });
        });
    }

    /** Daten für {@code /clan info}. */
    private record Info(List<ClanMember> members, int max, Component leader) {

        Info(List<ClanMember> members, Integer max) {
            this(members, max, Component.empty());
        }
    }

    void list(Player sender) {
        Social helper = social();
        helper.then(sender, clans().clanOf(sender.getUniqueId()), found -> {
            if (found.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
                return;
            }
            helper.then(sender, clans().members(found.get().id()).thenCompose(this::coloredMembers), members -> {
                sendRole(sender, "clan.list.leader", members, ClanRole.LEADER);
                sendRole(sender, "clan.list.moderators", members, ClanRole.MODERATOR);
                sendRole(sender, "clan.list.members", members, ClanRole.MEMBER);
                sendRole(sender, "clan.list.trials", members, ClanRole.TRIAL);
            });
        });
    }

    /** Mitglied mit farbigem Namen. */
    private record ColoredMember(ClanMember member, Component name) {
    }

    private CompletableFuture<List<ColoredMember>> coloredMembers(List<ClanMember> members) {
        List<CompletableFuture<ColoredMember>> names = members.stream().map(member -> social()
                .coloredName(member.uuid(), member.name()).thenApply(name -> new ColoredMember(member, name)))
                .toList();
        return CompletableFuture.allOf(names.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> names.stream().map(CompletableFuture::join).toList());
    }

    private void sendRole(Player sender, String key, List<ColoredMember> members, ClanRole role) {
        List<Component> names = members.stream().filter(member -> member.member().role() == role)
                .map(ColoredMember::name).toList();
        if (!names.isEmpty()) {
            social().send(sender, key, Formatter.joining("names", names));
        }
    }

    // ------------------------------------------------------------------ Verwaltung

    void rename(Player sender, String name, String tag) {
        Social helper = social();
        helper.then(sender, clans().rename(sender.getUniqueId(), name, tag), result -> {
            if (result.outcome() != ClanOutcome.RENAMED || result.clan() == null) {
                error(sender, result, sender.getUsername(), result.outcome() == ClanOutcome.TAG_TAKEN ? tag : name);
                return;
            }
            broadcast(result.clan().id(), "clan.renamed", List.of(), Social.unparsed("clan", name),
                    Social.unparsed("tag", tag));
        });
    }

    void delete(Player sender) {
        Social helper = social();
        helper.then(sender, clans().clanOf(sender.getUniqueId()).thenCompose(clan -> clan.isEmpty()
                ? CompletableFuture.completedFuture(List.<ClanMember>of()) : clans().members(clan.get().id()))
                .thenCombine(clans().delete(sender.getUniqueId()), Deleted::new), deleted -> {
                    if (deleted.result().outcome() != ClanOutcome.DELETED) {
                        error(sender, deleted.result(), sender.getUsername(), "");
                        return;
                    }
                    deleted.members().forEach(member -> helper.send(member.uuid(), "clan.deleted"));
                });
    }

    /** Ergebnis von {@code /clan delete confirm} mit den früheren Mitgliedern. */
    private record Deleted(List<ClanMember> members, ClanResult result) {
    }

    void settings(Player sender) {
        Social helper = social();
        helper.then(sender, clans().clanOf(sender.getUniqueId()), clan -> {
            if (clan.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
            } else {
                helper.get(ServerBridge.class).openGui(sender, GUI_SETTINGS, "");
            }
        });
    }

    // ------------------------------------------------------------------ Statistiken

    private static Optional<ModeKey> mode(String text) {
        try {
            return Optional.of(ModeKey.of(text.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Component modeChoice(Player sender, List<ModeKey> modes, String commandPrefix) {
        Social helper = social();
        Component line = helper.text(sender, "clan.cwstats.choose");
        for (ModeKey mode : modes) {
            line = line.append(Component.space()).append(helper.text(sender, "clan.cwstats.mode",
                    Social.unparsed("mode", mode.key())).clickEvent(ClickEvent.runCommand(commandPrefix + mode.key())));
        }
        return line;
    }

    void stats(Player sender, @Nullable String modeText) {
        Social helper = social();
        helper.then(sender, clans().clanOf(sender.getUniqueId()), found -> {
            if (found.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
                return;
            }
            Clan clan = found.get();
            if (modeText == null) {
                helper.then(sender, clans().statsModes(clan.id()), modes -> sender.sendMessage(modeChoice(sender,
                        modes.stream().sorted((a, b) -> a.key().compareTo(b.key())).toList(), "/clan stats ")));
                return;
            }
            Optional<ModeKey> mode = mode(modeText);
            if (mode.isEmpty()) {
                helper.send(sender, "clan.err.stats.disabled", Social.unparsed("mode", modeText));
                return;
            }
            helper.then(sender, clans().stats(clan.id(), mode.get()), stats -> {
                if (stats.isEmpty()) {
                    helper.send(sender, "clan.err.stats.disabled", Social.unparsed("mode", mode.get().key()));
                    return;
                }
                helper.send(sender, "clan.stats.header", Social.unparsed("clan", clan.name()),
                        Social.unparsed("mode", mode.get().key()));
                statLine(sender, "members", stats.get().members());
                statLine(sender, "kills", stats.get().kills());
                statLine(sender, "deaths", stats.get().deaths());
                statLine(sender, "games", stats.get().games());
                statLine(sender, "wins", stats.get().wins());
                statLine(sender, "points", stats.get().points());
            });
        });
    }

    private void statLine(Player sender, String label, long value) {
        Social helper = social();
        helper.send(sender, "clan.stats.line", Placeholder.component("label", helper.text(sender,
                "clan.stats.label." + label)), Social.unparsed("value", value));
    }

    void toggleStats(Player sender, String modeText) {
        Social helper = social();
        Optional<ModeKey> mode = mode(modeText);
        if (mode.isEmpty()) {
            helper.send(sender, "clan.err.stats.disabled", Social.unparsed("mode", modeText));
            return;
        }
        helper.then(sender, clans().toggleStatsMode(sender.getUniqueId(), mode.get()), result -> {
            switch (result.outcome()) {
                case STATS_ENABLED -> helper.send(sender, "clan.stats.toggle.on", Social.unparsed("mode",
                        mode.get().key()));
                case STATS_DISABLED -> helper.send(sender, "clan.stats.toggle.off", Social.unparsed("mode",
                        mode.get().key()));
                default -> error(sender, result, sender.getUsername(), "");
            }
        });
    }

    void warStats(Player sender, String clanName, @Nullable String modeText) {
        Social helper = social();
        helper.then(sender, clans().byName(clanName), found -> {
            if (found.isEmpty()) {
                helper.send(sender, "clan.err.unknown", Social.unparsed("clan", clanName));
                return;
            }
            Clan clan = found.get();
            if (modeText == null) {
                sender.sendMessage(modeChoice(sender, ClanTexts.WAR_MODES, "/clan cwstats " + clan.name() + " "));
                return;
            }
            Optional<ModeKey> mode = mode(modeText);
            CompletableFuture<Optional<ClanWarStats>> lookup = mode.isEmpty()
                    ? CompletableFuture.completedFuture(Optional.empty()) : clans().warStats(clan.id(), mode.get());
            helper.then(sender, lookup, stats -> {
                if (stats.isEmpty()) {
                    helper.send(sender, "clan.cwstats.empty", Social.unparsed("mode", modeText));
                    return;
                }
                ClanWarStats value = stats.get();
                helper.send(sender, "clan.cwstats.header", Social.unparsed("clan", clan.name()),
                        Social.unparsed("mode", value.mode().key()));
                helper.send(sender, "clan.cwstats.elo", Social.unparsed("elo", value.elo()));
                helper.send(sender, "clan.cwstats.rank", Social.unparsed("rank", value.rank()));
                helper.send(sender, "clan.cwstats.games", Social.unparsed("games", value.games()));
                helper.send(sender, "clan.cwstats.wins", Social.unparsed("wins", value.wins()));
                helper.send(sender, "clan.cwstats.losses", Social.unparsed("losses", value.losses()));
                helper.send(sender, "clan.cwstats.rate", Social.unparsed("rate",
                        value.winRateText().replace(" %", "")));
            });
        });
    }

    void level(Player sender) {
        Social helper = social();
        helper.then(sender, clans().clanOf(sender.getUniqueId()), found -> {
            if (found.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
                return;
            }
            helper.then(sender, clans().level(found.get().id()), progress -> helper.send(sender, "clan.level",
                    Social.unparsed("lvl", progress.level()), Social.unparsed("xp", progress.xp()),
                    Social.unparsed("need", progress.nextLevelXp())));
        });
    }

    void confirmReset(Player sender, String modeText) {
        Social helper = social();
        Optional<ModeKey> mode = mode(modeText);
        if (mode.isEmpty()) {
            helper.send(sender, "clan.err.stats.disabled", Social.unparsed("mode", modeText));
            return;
        }
        helper.then(sender, clans().role(sender.getUniqueId()), role -> {
            if (role.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
            } else if (role.get() != ClanRole.LEADER) {
                helper.send(sender, "clan.err.notleader");
            } else {
                helper.send(sender, "clan.reset.confirm", Social.unparsed("mode", mode.get().key()));
                sender.sendMessage(helper.text(sender, "clan.reset.button.yes")
                        .clickEvent(ClickEvent.runCommand("/clanstatsreset " + mode.get().key() + " confirm"))
                        .append(Component.space()).append(helper.text(sender, "clan.reset.button.no")));
            }
        });
    }

    void reset(Player sender, String modeText) {
        Social helper = social();
        Optional<ModeKey> mode = mode(modeText);
        if (mode.isEmpty()) {
            helper.send(sender, "clan.err.stats.disabled", Social.unparsed("mode", modeText));
            return;
        }
        helper.then(sender, clans().resetStats(sender.getUniqueId(), mode.get()), result -> {
            if (result.outcome() == ClanOutcome.STATS_RESET) {
                helper.send(sender, "clan.reset.done", Social.unparsed("mode", mode.get().key()));
            } else {
                error(sender, result, sender.getUsername(), "");
            }
        });
    }

    // ------------------------------------------------------------------ Clan-Chat und Meldungen

    void clanChat(Player sender, String text, boolean lead) {
        Social helper = social();
        ProxyChat guard = chat;
        if (guard == null) {
            return;
        }
        helper.then(sender, clans().role(sender.getUniqueId()), role -> {
            if (role.isEmpty()) {
                helper.send(sender, "clan.err.notinclan");
                return;
            }
            if (lead && !role.get().canUseLeadChat()) {
                helper.send(sender, "clan.err.notallowed");
                return;
            }
            helper.then(sender, guard.check(sender, text, lead ? ChatContext.Source.CLAN_LEAD_CHAT
                    : ChatContext.Source.CLAN_CHAT).thenCompose(checked -> checked.isEmpty()
                            ? CompletableFuture.completedFuture(Optional.<ClanChat>empty())
                            : clans().clanOf(sender.getUniqueId()).thenCompose(clan -> clan.isEmpty()
                                    ? CompletableFuture.completedFuture(Optional.<ClanChat>empty())
                                    : clans().members(clan.get().id()).thenApply(members -> Optional.of(new ClanChat(
                                            sender.getUniqueId(), members.stream()
                                                    .filter(member -> !lead || member.role().canUseLeadChat())
                                                    .map(ClanMember::uuid).toList(), checked.get(), lead))))),
                    message -> message.ifPresent(payload -> helper.log(helper.get(MessageBus.class)
                            .publish(MessageType.CLAN_CHAT, payload), "Clan-Chat senden")));
        });
    }

    void deliverChat(ClanChat message) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.nameOf(message.from()).thenCompose(name -> helper.coloredName(message.from(), name))
                .thenAccept(name -> message.to().forEach(uuid -> helper.send(uuid,
                        message.lead() ? "clan.chat.lead" : "clan.chat", Placeholder.component("name", name),
                        Placeholder.unparsed("text", message.text())))), "Clan-Chat zustellen");
    }

    void deliverStatus(ClanMemberStatus status, boolean online) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.nameOf(status.uuid()).thenCompose(name -> helper.coloredName(status.uuid(), name))
                .thenAccept(name -> status.targets().forEach(uuid -> helper.send(uuid,
                        online ? "clan.online" : "clan.offline", Placeholder.component("name", name)))),
                "Clan-Status zustellen");
    }

    /** Login: {@code last_active} und {@code CLAN_ONLINE} (Spec 01 §6.3 Nr. 6). */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        publishStatus(event.getPlayer().getUniqueId(), true);
    }

    /** Logout: {@code CLAN_OFFLINE} (Spec 01 §6.3 Nr. 7). */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        publishStatus(event.getPlayer().getUniqueId(), false);
    }

    private void publishStatus(UUID player, boolean online) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        ClanService service = helper.get(ClanService.class);
        CompletableFuture<?> active = online ? service.markActive(player) : CompletableFuture.completedFuture(null);
        helper.log(active.thenCompose(ignored -> service.clanOf(player)).thenCompose(clan -> clan.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : service.statusRecipients(player).thenCompose(targets -> targets.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : helper.get(MessageBus.class).publish(online ? MessageType.CLAN_ONLINE
                                : MessageType.CLAN_OFFLINE, new ClanMemberStatus(player, clan.get().id(), targets)))),
                "Clan-Status senden");
    }

    /** Fehlertext eines Clan-Ergebnisses; Erfolge werfen {@link IllegalArgumentException}. */
    public static String errorKey(ClanOutcome outcome) {
        return ClanTexts.error(outcome);
    }

    // ------------------------------------------------------------------ Inaktivität

    /** Zeit bis zum nächsten Lauf um 04:00 Europe/Berlin. */
    public static Duration untilNextRun(Instant now) {
        ZonedDateTime local = now.atZone(BERLIN);
        ZonedDateTime next = local.with(JOB_TIME);
        if (!next.isAfter(local)) {
            next = next.plusDays(1);
        }
        return Duration.between(local, next);
    }

    void deleteInactive() {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(helper.get(ClanService.class).deleteInactive(Instant.now().minus(ClanRules.INACTIVE_AFTER))
                .thenAccept(count -> {
                    if (count > 0) {
                        helper.core().log().info("{} inaktive Clans gelöscht", count);
                    }
                }), "Inaktive Clans löschen");
    }
}
