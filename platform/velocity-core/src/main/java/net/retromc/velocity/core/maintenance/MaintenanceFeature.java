package net.retromc.velocity.core.maintenance;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.server.MaintenanceService;
import net.retromc.core.api.server.MaintenanceState;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Wartungsmodus (Spec 01 §3.6 #87, §6.3 Nr. 1, §6.15): {@code /maintenance} ({@code /wartung}) mit
 * {@code on|off|add|remove|list}, Login-Sperre mit {@code screen.maintenance} (Whitelist oder Recht
 * {@code retromc.maintenance.bypass}), Countdown 60 s (60, 30, 10, 5–1) und danach Kick aller ohne Berechtigung –
 * auf jedem Proxy über {@code MAINTENANCE_CHANGED}. Die MOTD-Umschaltung übernimmt das MOTD-Feature.
 */
public final class MaintenanceFeature implements CoreFeature<VelocityCore> {

    public static final CommandName MAINTENANCE = CommandName.of("maintenance", "wartung");
    public static final List<CommandName> COMMANDS = List.of(MAINTENANCE);

    public static final String ADMIN = Social.permission("admin.maintenance");
    public static final String BYPASS = Social.permission("maintenance.bypass");

    private final List<Registration> subscriptions = new ArrayList<>();
    private final List<ScheduledTask> countdown = new ArrayList<>();
    private volatile MaintenanceState state = MaintenanceState.OFF;
    private @Nullable Social social;

    @Override
    public String name() {
        return "Wartung";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var command = MAINTENANCE.builder(manager).permission(ADMIN);
        manager.command(command.literal("on").handler(ctx -> switchOn(ctx.sender())));
        manager.command(command.literal("off").handler(ctx -> switchOff(ctx.sender())));
        manager.command(command.literal("add").required("player", stringParser(), helper.players())
                .handler(ctx -> whitelist(ctx.sender(), ctx.get("player"), true)));
        manager.command(command.literal("remove").required("player", stringParser())
                .handler(ctx -> whitelist(ctx.sender(), ctx.get("player"), false)));
        manager.command(command.literal("list").handler(ctx -> list(ctx.sender())));
        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.MAINTENANCE_CHANGED, this::onChanged).join());
        core.proxy().getEventManager().register(core.plugin(), this);
        state = core.services().get(MaintenanceService.class).state().join();
    }

    @Override
    public void disable(VelocityCore core) {
        cancelCountdown();
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Wartung ist nicht aktiv");
        }
        return current;
    }

    /** Darf der Spieler während der Wartung joinen (Spec 01 §6.15 Nr. 2)? */
    public static boolean allowed(MaintenanceState state, boolean whitelisted, boolean bypass) {
        return !state.enabled() || whitelisted || bypass;
    }

    // ------------------------------------------------------------------ Befehle

    void switchOn(CommandSource source) {
        Social helper = social();
        UUID actor = source instanceof Player player ? player.getUniqueId() : null;
        helper.then(source, helper.get(MaintenanceService.class).enable(actor, null), enabled -> {
            state = enabled;
            helper.send(source, "maintenance.on");
            startCountdown();
        });
    }

    void switchOff(CommandSource source) {
        Social helper = social();
        UUID actor = source instanceof Player player ? player.getUniqueId() : null;
        helper.then(source, helper.get(MaintenanceService.class).disable(actor), disabled -> {
            state = disabled;
            cancelCountdown();
            helper.send(source, "maintenance.off");
        });
    }

    void whitelist(CommandSource source, String name, boolean add) {
        Social helper = social();
        helper.then(source, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(source, "error.player.unknown", Social.player(name));
                return;
            }
            MaintenanceService maintenance = helper.get(MaintenanceService.class);
            UUID actor = source instanceof Player player ? player.getUniqueId() : null;
            CompletableFuture<Boolean> done = add ? maintenance.addToWhitelist(target.get().uuid(), actor)
                    : maintenance.removeFromWhitelist(target.get().uuid());
            helper.then(source, done, changed -> helper.send(source, add ? "maintenance.added"
                    : "maintenance.removed", Social.player(target.get().name())));
        });
    }

    void list(CommandSource source) {
        Social helper = social();
        helper.then(source, helper.get(MaintenanceService.class).whitelist(), players -> {
            helper.send(source, "maintenance.list.header", Social.unparsed("n", players.size()));
            players.forEach(uuid -> helper.then(source, helper.nameOf(uuid),
                    name -> helper.send(source, "maintenance.list.line", Social.player(name))));
        });
    }

    // ------------------------------------------------------------------ Countdown und Kick

    private void onChanged(Envelope<MaintenanceChanged> envelope) {
        Social helper = social;
        if (helper == null || envelope.src().equals(helper.get(MessageBus.class).source())) {
            return;
        }
        helper.log(helper.get(MaintenanceService.class).state().thenAccept(current -> {
            state = current;
            if (current.enabled()) {
                startCountdown();
            } else {
                cancelCountdown();
            }
        }), "Wartungsstatus laden");
    }

    private synchronized void startCountdown() {
        Social helper = social();
        if (!countdown.isEmpty()) {
            return;
        }
        long total = MaintenanceService.COUNTDOWN.toSeconds();
        for (int seconds : MaintenanceService.ANNOUNCE_SECONDS) {
            countdown.add(helper.core().proxy().getScheduler().buildTask(helper.core().plugin(), () -> announce(
                    seconds)).delay(total - seconds, TimeUnit.SECONDS).schedule());
        }
        countdown.add(helper.core().proxy().getScheduler().buildTask(helper.core().plugin(), this::kickAll)
                .delay(total, TimeUnit.SECONDS).schedule());
    }

    private synchronized void cancelCountdown() {
        countdown.forEach(ScheduledTask::cancel);
        countdown.clear();
    }

    private void announce(int seconds) {
        Social helper = social;
        if (helper == null || !state.enabled()) {
            return;
        }
        String key = seconds == 1 ? "maintenance.countdown.one" : "maintenance.countdown";
        helper.core().proxy().getAllPlayers().forEach(player -> helper.send(player, key,
                Social.unparsed("s", seconds)));
    }

    private void kickAll() {
        Social helper = social;
        synchronized (this) {
            countdown.clear();
        }
        if (helper == null || !state.enabled()) {
            return;
        }
        MaintenanceService maintenance = helper.get(MaintenanceService.class);
        for (Player player : helper.core().proxy().getAllPlayers()) {
            if (player.hasPermission(BYPASS)) {
                continue;
            }
            helper.log(maintenance.isWhitelisted(player.getUniqueId()).thenAccept(whitelisted -> {
                if (!allowed(state, whitelisted, false)) {
                    player.disconnect(helper.text(player, "screen.maintenance"));
                }
            }), "Wartungs-Kick");
        }
    }

    // ------------------------------------------------------------------ Login

    /** Während der Wartung nur Whitelist oder Recht {@code maintenance.bypass} (Spec 01 §6.3 Nr. 1). */
    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Social helper = social;
        MaintenanceState current = state;
        Player player = event.getPlayer();
        if (helper == null || !event.getResult().isAllowed() || allowed(current, false, player.hasPermission(BYPASS))) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        Locale locale = player.getEffectiveLocale() == null ? Messages.DEFAULT_LOCALE : player.getEffectiveLocale();
        CompletableFuture<Void> check = helper.get(MaintenanceService.class).isWhitelisted(player.getUniqueId())
                .thenAccept(whitelisted -> {
                    if (!allowed(current, whitelisted, false)) {
                        event.setResult(ResultedEvent.ComponentResult.denied(helper.get(I18n.class)
                                .render(locale, "screen.maintenance")));
                    }
                }).exceptionally(error -> {
                    helper.core().log().error("Wartungsprüfung für {} fehlgeschlagen", player.getUsername(), error);
                    return null;
                });
        return EventTask.resumeWhenComplete(check);
    }
}
