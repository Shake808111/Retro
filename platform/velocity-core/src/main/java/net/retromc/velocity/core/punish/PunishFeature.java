package net.retromc.velocity.core.punish;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.event.Kick;
import net.retromc.core.api.event.PunishIssued;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentDuration;
import net.retromc.core.api.punish.PunishmentId;
import net.retromc.core.api.punish.PunishmentOrigin;
import net.retromc.core.api.punish.PunishmentReason;
import net.retromc.core.api.punish.PunishmentRequest;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.util.IpHmac;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.ModerationTexts;
import net.retromc.platform.common.social.SocialConfig;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Strafen auf dem Proxy (Spec 01 §3.6 #77–82, §6.3 Nr. 2, §6.10): {@code /ban /mute /kick /warn /unban /unmute
 * /history}, Login-Sperre mit Ban-Bildschirm, Sofort-Kick bei Bann, Mute-/Verwarnungs-Hinweise und Kick über
 * {@code net:moderation} (Multi-Proxy). Gründe, Stufen und IDs liefert der {@link PunishmentService}.
 */
public final class PunishFeature implements CoreFeature<VelocityCore> {

    public static final CommandName BAN = CommandName.of("ban");
    public static final CommandName MUTE = CommandName.of("mute");
    public static final CommandName KICK = CommandName.of("kick");
    public static final CommandName WARN = CommandName.of("warn");
    public static final CommandName UNBAN = CommandName.of("unban", "pardon");
    public static final CommandName UNMUTE = CommandName.of("unmute");
    public static final CommandName HISTORY = CommandName.of("history");
    public static final List<CommandName> COMMANDS = List.of(BAN, MUTE, KICK, WARN, UNBAN, UNMUTE, HISTORY);

    /** Akteur für Befehle aus der Konsole. */
    static final UUID CONSOLE = new UUID(0, 0);

    private final List<Registration> subscriptions = new ArrayList<>();
    private volatile List<PunishmentReason> reasons = List.of();
    private @Nullable Social social;
    private @Nullable SocialConfig config;

    @Override
    public String name() {
        return "Strafen";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        config = SocialConfig.read(EnvReader.system());
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        manager.command(BAN.builder(manager).permission(Social.permission("punish.ban"))
                .required("player", stringParser(), helper.players())
                .required("reason", stringParser(), reasonSuggestions(PunishmentType.BAN))
                .optional("duration", stringParser())
                .handler(ctx -> punish(ctx.sender(), PunishmentType.BAN, ctx.get("player"), ctx.get("reason"),
                        ctx.<String>optional("duration").orElse(null))));
        manager.command(MUTE.builder(manager).permission(Social.permission("punish.mute"))
                .required("player", stringParser(), helper.players())
                .required("reason", stringParser(), reasonSuggestions(PunishmentType.MUTE))
                .optional("duration", stringParser())
                .handler(ctx -> punish(ctx.sender(), PunishmentType.MUTE, ctx.get("player"), ctx.get("reason"),
                        ctx.<String>optional("duration").orElse(null))));
        manager.command(KICK.builder(manager).permission(Social.permission("punish.kick"))
                .required("player", stringParser(), helper.players())
                .required("text", greedyStringParser())
                .handler(ctx -> note(ctx.sender(), PunishmentType.KICK, ctx.get("player"), ctx.get("text"))));
        manager.command(WARN.builder(manager).permission(Social.permission("punish.warn"))
                .required("player", stringParser(), helper.players())
                .required("text", greedyStringParser())
                .handler(ctx -> note(ctx.sender(), PunishmentType.WARN, ctx.get("player"), ctx.get("text"))));
        manager.command(UNBAN.builder(manager).permission(Social.permission("punish.unban"))
                .required("target", stringParser(), helper.players())
                .required("note", greedyStringParser())
                .handler(ctx -> revoke(ctx.sender(), PunishmentType.BAN, ctx.get("target"), ctx.get("note"))));
        manager.command(UNMUTE.builder(manager).permission(Social.permission("punish.unmute"))
                .required("target", stringParser(), helper.players())
                .required("note", greedyStringParser())
                .handler(ctx -> revoke(ctx.sender(), PunishmentType.MUTE, ctx.get("target"), ctx.get("note"))));
        manager.command(HISTORY.builder(manager).permission(Social.permission("punish.history"))
                .required("player", stringParser(), helper.players())
                .handler(ctx -> history(ctx.sender(), ctx.get("player"))));

        MessageBus bus = core.services().get(MessageBus.class);
        subscriptions.add(bus.subscribe(MessageType.PUNISH_ISSUED, envelope -> onIssued(envelope.d())).join());
        subscriptions.add(bus.subscribe(MessageType.KICK, envelope -> onKick(envelope.d())).join());
        core.proxy().getEventManager().register(core.plugin(), this);
        helper.log(core.services().get(PunishmentService.class).reasons().thenAccept(list -> reasons = list),
                "Strafgründe laden");
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
            throw new IllegalStateException("Strafen sind nicht aktiv");
        }
        return current;
    }

    private ModerationTexts texts() {
        return new ModerationTexts(social().get(I18n.class));
    }

    private SuggestionProvider<CommandSource> reasonSuggestions(PunishmentType type) {
        return SuggestionProvider.blocking((context, input) -> reasons.stream()
                .filter(reason -> reason.type() == type).map(reason -> Suggestion.suggestion(reason.code()))
                .toList());
    }

    private static UUID actor(CommandSource source) {
        return source instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private static @Nullable String server(CommandSource source) {
        return source instanceof Player player ? Social.serverName(player).orElse(null) : null;
    }

    // ------------------------------------------------------------------ /ban, /mute

    /** Grund und Ziel eines Strafbefehls. */
    private record Target(Optional<PunishmentReason> reason, Optional<PlayerRef> player) {
    }

    void punish(CommandSource source, PunishmentType type, String name, String code, @Nullable String durationText) {
        Social helper = social();
        Optional<PunishmentDuration> override = durationText == null ? Optional.empty()
                : PunishmentDuration.parse(durationText);
        if (durationText != null && override.isEmpty()) {
            helper.send(source, "punish.err.duration", Social.unparsed("input", durationText));
            return;
        }
        PunishmentService punishments = helper.get(PunishmentService.class);
        String lower = code.toLowerCase(Locale.ROOT);
        helper.then(source, punishments.reason(lower).thenCombine(helper.resolve(name), Target::new), target -> {
            if (target.reason().isEmpty() || target.reason().get().type() != type) {
                helper.send(source, "punish.err.reason", Social.unparsed("reason", code),
                        Social.unparsed("reasons", reasons.stream().filter(reason -> reason.type() == type)
                                .map(PunishmentReason::code).collect(Collectors.joining(", "))));
                return;
            }
            if (target.player().isEmpty()) {
                helper.send(source, "error.player.unknown", Social.player(name));
                return;
            }
            PlayerRef player = target.player().get();
            SocialConfig settings = config;
            IpHmac ip = type == PunishmentType.BAN && settings != null
                    ? helper.online(player.uuid()).flatMap(online -> settings.ip(online.getRemoteAddress()
                            .getAddress())).orElse(null)
                    : null;
            PunishmentRequest request = new PunishmentRequest(player.uuid(), lower, null, actor(source),
                    PunishmentOrigin.MANUAL, null, override.orElse(null), server(source), null, ip);
            helper.then(source, punishments.issue(request), punishment -> source.sendMessage(
                    texts().issued(helper.locale(source), punishment, player.name())));
        });
    }

    // ------------------------------------------------------------------ /kick, /warn

    void note(CommandSource source, PunishmentType type, String name, String text) {
        Social helper = social();
        helper.then(source, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(source, "error.player.unknown", Social.player(name));
                return;
            }
            PlayerRef player = target.get();
            if (type == PunishmentType.KICK && helper.online(player.uuid()).isEmpty()
                    && helper.services().find(PresenceService.class).isEmpty()) {
                helper.send(source, "error.player.offline", Social.player(player.name()));
                return;
            }
            PunishmentService punishments = helper.get(PunishmentService.class);
            CompletableFuture<Punishment> done = type == PunishmentType.KICK
                    ? punishments.kick(player.uuid(), text, actor(source), server(source))
                    : punishments.warn(player.uuid(), text, actor(source), server(source));
            helper.then(source, done, punishment -> source.sendMessage(texts().issued(helper.locale(source),
                    punishment, player.name())));
        });
    }

    // ------------------------------------------------------------------ /unban, /unmute

    void revoke(CommandSource source, PunishmentType type, String target, String note) {
        Social helper = social();
        PunishmentService punishments = helper.get(PunishmentService.class);
        CompletableFuture<Optional<Punishment>> byId = PunishmentId.isPunishmentId(target)
                ? punishments.byPublicId(target).thenCompose(found -> found.isPresent()
                        && found.get().type() == type && found.get().revokedAt() == null
                        ? punishments.revoke(target, actor(source), note)
                        : CompletableFuture.completedFuture(Optional.empty()))
                : CompletableFuture.completedFuture(Optional.empty());
        helper.then(source, byId.thenCompose(revoked -> revoked.isPresent()
                ? CompletableFuture.completedFuture(revoked)
                : helper.resolve(target).thenCompose(player -> player.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<Punishment>empty())
                        : punishments.revokeActive(player.get().uuid(), type, actor(source), note))), revoked -> {
                            if (revoked.isEmpty()) {
                                helper.send(source, "punish.err.notfound", Social.unparsed("input", target));
                            } else {
                                helper.send(source, "punish.revoked", Social.unparsed("id", revoked.get().publicId()));
                            }
                        });
    }

    // ------------------------------------------------------------------ /history

    void history(CommandSource source, String name) {
        Social helper = social();
        helper.then(source, helper.resolve(name), target -> {
            if (target.isEmpty()) {
                helper.send(source, "error.player.unknown", Social.player(name));
                return;
            }
            helper.then(source, helper.get(PunishmentService.class).history(target.get().uuid()), list -> {
                helper.send(source, "history.header", Social.player(target.get().name()));
                if (list.isEmpty()) {
                    helper.send(source, "history.empty");
                    return;
                }
                ModerationTexts texts = texts();
                Locale locale = helper.locale(source);
                Instant now = Instant.now();
                list.forEach(punishment -> source.sendMessage(texts.historyLine(locale, punishment, now)));
            });
        });
    }

    // ------------------------------------------------------------------ Login und Ereignisse

    /** Login mit aktivem Bann ablehnen (Spec 01 §6.3 Nr. 2, inklusive IP-Bann über {@code ban_ip}). */
    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Social helper = social;
        SocialConfig settings = config;
        if (helper == null || settings == null || !event.getResult().isAllowed()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        Player player = event.getPlayer();
        Locale locale = player.getEffectiveLocale() == null ? Messages.DEFAULT_LOCALE : player.getEffectiveLocale();
        CompletableFuture<Void> check = helper.get(PunishmentService.class)
                .activeBan(player.getUniqueId(), settings.ip(player.getRemoteAddress().getAddress()).orElse(null))
                .thenAccept(ban -> ban.ifPresent(punishment -> event.setResult(ResultedEvent.ComponentResult.denied(
                        texts().banScreen(locale, punishment, Instant.now(), settings.appealUrl())))))
                .exceptionally(error -> {
                    helper.core().log().error("Bann-Prüfung für {} fehlgeschlagen", player.getUsername(), error);
                    return null;
                });
        return EventTask.resumeWhenComplete(check);
    }

    /** Mute-Cache füllen (Spec 01 §6.3 Nr. 4). */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Social helper = social;
        if (helper != null) {
            helper.log(helper.get(PunishmentService.class).activeMute(event.getPlayer().getUniqueId()),
                    "Mute-Cache füllen");
        }
    }

    void onIssued(PunishIssued issued) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Optional<Player> online = helper.online(issued.uuid());
        if (online.isEmpty()) {
            return;
        }
        Player player = online.get();
        PunishmentService punishments = helper.get(PunishmentService.class);
        helper.log(punishments.byPublicId(issued.id()).thenAccept(found -> found.ifPresent(punishment -> {
            Locale locale = helper.locale(player);
            switch (punishment.type()) {
                case BAN -> player.disconnect(texts().banKick(locale, punishment));
                case MUTE -> player.sendMessage(texts().muteNotice(locale, punishment, Instant.now()));
                case WARN -> helper.send(player, "warn.notice", Placeholder.component("reason",
                        texts().reason(locale, punishment)));
                case KICK -> {
                    // Kick kommt als eigenes Ereignis.
                }
                default -> throw new IllegalStateException("Unbekannter Typ " + punishment.type());
            }
        })), "Strafe zustellen");
    }

    void onKick(Kick kick) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.online(kick.uuid()).ifPresent(player -> player.disconnect(
                texts().kickScreen(helper.locale(player), kick.reason())));
    }
}
