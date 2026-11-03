package net.retromc.velocity.core.report;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.event.ReportClosed;
import net.retromc.core.api.event.ReportNew;
import net.retromc.core.api.punish.Report;
import net.retromc.core.api.punish.ReportReason;
import net.retromc.core.api.punish.ReportRequest;
import net.retromc.core.api.punish.ReportResult;
import net.retromc.core.api.punish.ReportService;
import net.retromc.core.api.punish.ReportStatus;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.SocialConfig;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Meldungen (Spec 01 §3.6 #73–76, #83–85, §6.9): {@code /report} mit Gründen und Bestätigung, {@code /chatlog},
 * {@code /reports}, {@code /report close|claim}, {@code /goto}. Neue Meldungen erreichen das Team auf allen Proxys
 * ({@code REPORT_NEW}, Präfix „Wächter“); Ergebnisse den Melder sofort oder beim nächsten Login.
 */
public final class ReportFeature implements CoreFeature<VelocityCore> {

    public static final CommandName REPORT = CommandName.of("report");
    public static final CommandName REPORTS = CommandName.of("reports");
    public static final CommandName CHATLOG = CommandName.of("chatlog");
    public static final CommandName GOTO = CommandName.of("goto");
    public static final List<CommandName> COMMANDS = List.of(REPORT, REPORTS, CHATLOG, GOTO);

    /** Team-Recht für {@code /reports} und Benachrichtigungen. */
    public static final String VIEW = Social.permission("report.view");

    private final List<Registration> subscriptions = new ArrayList<>();
    private @Nullable Social social;
    private @Nullable SocialConfig config;

    @Override
    public String name() {
        return "Reports";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        config = SocialConfig.read(EnvReader.system());
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var report = REPORT.builder(manager).senderType(Player.class);
        manager.command(report.literal("close").permission(VIEW).required("id", stringParser())
                .required("result", stringParser()).handler(ctx -> close(ctx.sender(), ctx.get("id"),
                        ctx.get("result"))));
        manager.command(report.literal("claim").permission(VIEW).required("id", stringParser())
                .handler(ctx -> claim(ctx.sender(), ctx.get("id"))));
        manager.command(report.required("player", stringParser(), helper.players())
                .handler(ctx -> choose(ctx.sender(), ctx.get("player"))));
        manager.command(report.required("player", stringParser(), helper.players()).required("reason", stringParser())
                .handler(ctx -> confirm(ctx.sender(), ctx.get("player"), ctx.get("reason"))));
        manager.command(report.required("player", stringParser(), helper.players()).required("reason", stringParser())
                .literal("confirm").handler(ctx -> create(ctx.sender(), ctx.get("player"), ctx.get("reason"))));
        manager.command(REPORTS.builder(manager).permission(VIEW).handler(ctx -> open(ctx.sender())));
        manager.command(CHATLOG.builder(manager).senderType(Player.class)
                .required("player", stringParser(), helper.players())
                .handler(ctx -> chatlog(ctx.sender(), ctx.get("player"))));
        manager.command(GOTO.builder(manager).senderType(Player.class).permission(Social.permission("staff.goto"))
                .required("player", stringParser(), helper.players())
                .handler(ctx -> jump(ctx.sender(), ctx.get("player"))));
        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.REPORT_NEW, envelope -> notifyStaff(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.REPORT_CLOSED, envelope -> notifyReporter(envelope.d())).join());
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
        config = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Reports sind nicht aktiv");
        }
        return current;
    }

    private String server(Player player) {
        return Social.serverName(player).orElse("proxy");
    }

    // ------------------------------------------------------------------ /report

    /** {@code /report <Spieler>}: Klickliste der am Server zulässigen Gründe. */
    void choose(Player sender, String name) {
        Social helper = social();
        helper.send(sender, "report.choose", Social.player(name));
        for (ReportReason reason : ReportReason.allowedFor(helper.mode(server(sender)))) {
            sender.sendMessage(helper.text(sender, "report.reason.line", Social.unparsed("reason", reason.key()))
                    .clickEvent(ClickEvent.runCommand("/report " + name + " " + reason.key())));
        }
    }

    /** {@code /report <Spieler> <Grund>}: Bestätigungsfrage. */
    void confirm(Player sender, String name, String reasonKey) {
        Social helper = social();
        Optional<ReportReason> reason = ReportReason.byKey(reasonKey)
                .filter(value -> value.allowedOn(helper.mode(server(sender))));
        if (reason.isEmpty()) {
            helper.send(sender, "report.err.reason");
            return;
        }
        helper.send(sender, "report.confirm", Social.player(name), Social.unparsed("reason", reason.get().key()));
        sender.sendMessage(helper.clickLine(sender, "prefix.report", "click.confirm",
                "/report " + name + " " + reason.get().key() + " confirm"));
    }

    void create(Player sender, String name, String reasonKey) {
        Social helper = social();
        Optional<ReportReason> reason = ReportReason.byKey(reasonKey);
        if (reason.isEmpty()) {
            helper.send(sender, "report.err.reason");
            return;
        }
        String server = server(sender);
        helper.then(sender, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(sender, "error.player.unknown", Social.player(name));
                return;
            }
            ReportRequest request = new ReportRequest(sender.getUniqueId(), target.get().uuid(), reason.get(), server,
                    helper.mode(server), null);
            helper.then(sender, helper.get(ReportService.class).create(request),
                    result -> helper.send(sender, resultKey(result.outcome()), Social.player(target.get().name())));
        });
    }

    static String resultKey(ReportResult.Outcome outcome) {
        return switch (outcome) {
            case CREATED -> "report.thanks";
            case SELF -> "report.err.self";
            case COOLDOWN -> "report.err.cooldown";
            case LIMIT -> "report.err.limit";
            case REASON_NOT_ALLOWED -> "report.err.reason";
            case STAFF -> "report.err.staff";
            case UNKNOWN_PLAYER -> "error.player.unknown";
        };
    }

    // ------------------------------------------------------------------ Team

    void open(CommandSource sender) {
        Social helper = social();
        helper.then(sender, helper.get(ReportService.class).open(), reports -> {
            if (reports.isEmpty()) {
                helper.send(sender, "reports.empty");
                return;
            }
            helper.send(sender, "reports.header", Social.unparsed("n", reports.size()));
            for (Report report : reports) {
                helper.then(sender, names(report.reporter(), report.target()), names -> sender.sendMessage(
                        helper.text(sender, "reports.line", line(report, names))
                                .append(Component.space())
                                .append(helper.text(sender, "click.take")
                                        .clickEvent(ClickEvent.runCommand("/report claim " + report.publicId())))));
            }
        });
    }

    private CompletableFuture<List<String>> names(UUID reporter, UUID target) {
        Social helper = social();
        return helper.nameOf(reporter).thenCombine(helper.nameOf(target), List::of);
    }

    private static TagResolver line(Report report, List<String> names) {
        return TagResolver.resolver(Social.unparsed("id", report.publicId()), Social.unparsed("reporter",
                names.get(0)), Social.player(names.get(1)), Social.unparsed("reason", report.reason().key()),
                Social.unparsed("server", report.server()));
    }

    void claim(Player staff, String id) {
        Social helper = social();
        helper.then(staff, helper.get(ReportService.class).claim(id, staff.getUniqueId()), claimed -> {
            if (claimed.isEmpty()) {
                helper.send(staff, "report.err.notfound", Social.unparsed("id", id));
                return;
            }
            helper.send(staff, "report.claimed", Social.unparsed("id", id));
            helper.then(staff, helper.nameOf(claimed.get().target()), name -> jump(staff, name));
        });
    }

    void close(Player staff, String id, String result) {
        Social helper = social();
        String value = result.toLowerCase(Locale.ROOT);
        if (!value.equals("ok") && !value.equals("abgelehnt")) {
            helper.send(staff, "error.command.usage", Social.unparsed("syntax", "report close <ID> <ok|abgelehnt>"));
            return;
        }
        helper.then(staff, helper.get(ReportService.class).close(id, staff.getUniqueId(), value.equals("ok")),
                closed -> helper.send(staff, closed.isPresent() ? "report.closed" : "report.err.notfound",
                        Social.unparsed("id", id)));
    }

    /** {@code /goto}: auf den Server des Spielers (Proxy oder Präsenz). */
    void jump(Player staff, String name) {
        Social helper = social();
        Optional<Player> local = helper.online(name);
        CompletableFuture<Optional<String>> server = local.isPresent()
                ? CompletableFuture.completedFuture(Social.serverName(local.get()))
                : helper.resolve(name).thenCompose(target -> target.isEmpty()
                        || helper.services().find(PresenceService.class).isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<String>empty())
                        : helper.get(PresenceService.class).serverOf(target.get().uuid()));
        helper.then(staff, server, found -> {
            Optional<RegisteredServer> target = found.flatMap(value -> helper.core().proxy().getServer(value));
            if (target.isEmpty()) {
                helper.send(staff, "error.player.offline", Social.player(name));
                return;
            }
            helper.send(staff, "goto.ok", Social.player(name), Social.unparsed("server", found.get()));
            staff.createConnectionRequest(target.get()).fireAndForget();
        });
    }

    // ------------------------------------------------------------------ /chatlog

    void chatlog(Player sender, String name) {
        Social helper = social();
        SocialConfig settings = config;
        helper.then(sender, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(sender, "error.player.unknown", Social.player(name));
                return;
            }
            helper.then(sender, helper.get(ReportService.class).createChatlog(sender.getUniqueId(),
                    target.get().uuid(), server(sender)), chatlog -> {
                        if (chatlog.isEmpty() || settings == null) {
                            helper.send(sender, "chatlog.err.empty", Social.player(target.get().name()));
                        } else {
                            helper.send(sender, "chatlog.created", Social.player(target.get().name()),
                                    Social.unparsed("url", settings.chatlogUrl(chatlog.get().id())));
                        }
                    });
        });
    }

    // ------------------------------------------------------------------ Zustellung

    void notifyStaff(ReportNew report) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        List<Player> staff = helper.core().proxy().getAllPlayers().stream()
                .filter(player -> player.hasPermission(VIEW)).toList();
        if (staff.isEmpty()) {
            return;
        }
        helper.log(names(report.reporter(), report.target()).thenAccept(names -> staff.forEach(player ->
                player.sendMessage(helper.text(player, "report.staff.new", Social.unparsed("reporter", names.get(0)),
                        Social.player(names.get(1)), Social.unparsed("reason", report.reason()),
                        Social.unparsed("server", report.server()))
                        .append(Component.space())
                        .append(helper.text(player, "click.take")
                                .clickEvent(ClickEvent.runCommand("/report claim " + report.id())))))),
                "Team über Meldung informieren");
    }

    void notifyReporter(ReportClosed closed) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.online(closed.reporter()).ifPresent(player -> helper.log(helper.nameOf(closed.target())
                .thenAccept(name -> helper.send(player, closed.justified() ? "report.result.ok"
                        : "report.result.rejected", Social.player(name))), "Report-Ergebnis zustellen"));
    }

    /** Ergebnisse, die während der Abwesenheit entschieden wurden (Spec 01 §6.9 Nr. 5). */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Player player = event.getPlayer();
        helper.log(helper.get(ProfileService.class).lastQuit(player.getUniqueId()).thenCompose(since -> since.isEmpty()
                ? CompletableFuture.completedFuture(List.<Report>of())
                : helper.get(ReportService.class).resultsSince(player.getUniqueId(), since.get()))
                .thenAccept(results -> results.forEach(report -> helper.log(helper.nameOf(report.target())
                        .thenAccept(name -> helper.send(player, report.status() == ReportStatus.ACCEPTED
                                ? "report.result.ok" : "report.result.rejected", Social.player(name))),
                        "Report-Ergebnis zustellen"))), "Report-Ergebnisse laden");
    }
}
