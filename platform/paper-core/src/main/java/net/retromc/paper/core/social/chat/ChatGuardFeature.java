package net.retromc.paper.core.social.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatGuard;
import net.retromc.core.api.punish.ChatLine;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.punish.ReportService;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.ModerationTexts;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jspecify.annotations.Nullable;

/**
 * Durchsetzung im normalen Chat auf dem Backend (Spec 01 §6.7 Nr. 2, §6.10, §6.16; Velocity bricht signierten Chat
 * nie ab): Mute ({@code mute.notice}), Anti-Spam ({@code spam.*}, Großbuchstaben → klein, Werbung mit Team-Hinweis),
 * Ignorieren (Empfänger, die den Absender ignorieren, sehen die Zeile nicht) und Chatpuffer für {@code /chatlog}.
 * Das Chat-Format rendert die Kern-UI (Spec 02). Bei Datenfehlern wird der Chat nicht blockiert.
 */
public final class ChatGuardFeature implements CoreFeature<PaperCore>, Listener {

    /** Höchste Wartezeit auf Dienste im asynchronen Chat-Thread. */
    static final Duration TIMEOUT = Duration.ofSeconds(2);
    /** Team-Hinweis bei Werbung. */
    static final String STAFF_NOTIFY = BuildInfo.BRAND_ID + ".report.view";

    private @Nullable PaperCore core;

    @Override
    public String name() {
        return "Chat-Schutz";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        core = null;
    }

    private static <T> T await(CompletableFuture<T> future) throws ExecutionException, InterruptedException,
            TimeoutException {
        return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        try {
            RankInfo rank = await(context.services().get(RankService.class).rank(uuid));
            ChatCheck check = await(context.services().get(ChatGuard.class).check(uuid, text,
                    new ChatContext(ChatContext.Source.CHAT, rank.weight(), rank.isTeam())));
            if (!check.verdict().allowed()) {
                event.setCancelled(true);
                answer(context, player, text, check);
                return;
            }
            if (!check.text().equals(text)) {
                event.message(Component.text(check.text()));
            }
            filterIgnoring(context, uuid, event.viewers());
            context.services().get(ReportService.class).bufferChat(context.config().name(),
                    new ChatLine(uuid, player.getName(), check.text(), Instant.now()))
                    .exceptionally(error -> {
                        context.log().warn("Chatpuffer nicht geschrieben", error);
                        return null;
                    });
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            context.log().warn("Chat-Prüfung für {} fehlgeschlagen, Nachricht wird zugelassen", player.getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Empfänger entfernen, die den Absender ignorieren (Spec 01 §6.7 Nr. 2). */
    static void filterIgnoring(PaperCore context, UUID sender, Set<Audience> viewers)
            throws ExecutionException, InterruptedException, TimeoutException {
        IgnoreService ignores = context.services().get(IgnoreService.class);
        List<Player> players = new ArrayList<>();
        List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player target && !target.getUniqueId().equals(sender)) {
                players.add(target);
                checks.add(ignores.ignores(target.getUniqueId(), sender));
            }
        }
        await(CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)));
        for (int i = 0; i < players.size(); i++) {
            if (checks.get(i).join()) {
                viewers.remove(players.get(i));
            }
        }
    }

    private void answer(PaperCore context, Player player, String text, ChatCheck check)
            throws ExecutionException, InterruptedException, TimeoutException {
        I18n i18n = context.services().get(I18n.class);
        var locale = i18n.locale(player.getUniqueId());
        switch (check.verdict()) {
            case MUTED -> {
                Optional<Punishment> mute = await(context.services().get(PunishmentService.class)
                        .activeMute(player.getUniqueId()));
                mute.ifPresent(punishment -> player.sendMessage(new ModerationTexts(i18n).muteNotice(locale,
                        punishment, Instant.now())));
            }
            case SPAM_WAIT -> player.sendMessage(i18n.render(locale, "spam.wait"));
            case REPEAT -> player.sendMessage(i18n.render(locale, "spam.repeat"));
            case BURST -> player.sendMessage(i18n.render(locale, "spam.burst", Placeholder.unparsed("s",
                    Long.toString(Math.max(1, (check.retryAfter().toMillis() + 999) / 1000)))));
            case AD -> {
                player.sendMessage(i18n.render(locale, "spam.ad"));
                for (Player staff : context.plugin().getServer().getOnlinePlayers()) {
                    if (staff.hasPermission(STAFF_NOTIFY)) {
                        staff.sendMessage(i18n.render(i18n.locale(staff.getUniqueId()), "spam.staff.ad",
                                Placeholder.unparsed("player", player.getName()),
                                Placeholder.unparsed("server", context.config().name()),
                                Placeholder.unparsed("text", text)));
                    }
                }
            }
            case OK, CAPS_FIXED -> {
                // erlaubt, wird oben behandelt
            }
        }
    }
}
