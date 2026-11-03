package net.retromc.velocity.core.spam;

import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatGuard;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.platform.common.social.ModerationTexts;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.social.Social;

/**
 * Mute und Anti-Spam für Proxy-Befehle {@code /msg /r /cc /clc} (Spec 01 §6.10, §6.16). Den normalen Chat prüft
 * paper-core; der Proxy bricht signierte Chatnachrichten nie ab. Gesperrte Nachrichten werden hier beantwortet
 * ({@code mute.notice}, {@code spam.*}); Werbung meldet {@code spam.staff.ad} an das Team auf diesem Proxy.
 */
public final class ProxyChat {

    /** Team-Hinweise (Werbung). */
    public static final String STAFF_NOTIFY = Social.permission("report.view");

    private final Social social;

    public ProxyChat(Social social) {
        this.social = Objects.requireNonNull(social, "social");
    }

    /**
     * Prüft die Nachricht.
     *
     * @return der zu sendende Text (bei Großbuchstaben korrigiert) oder leer, wenn sie gesperrt ist
     */
    public CompletableFuture<Optional<String>> check(Player player, String text, ChatContext.Source source) {
        return social.get(RankService.class).rank(player.getUniqueId()).exceptionally(error -> null)
                .thenCompose(rank -> social.get(ChatGuard.class).check(player.getUniqueId(), text,
                        new ChatContext(source, rank == null ? 0 : rank.weight(), rank != null && isTeam(rank))))
                .thenCompose(check -> answer(player, text, check));
    }

    private static boolean isTeam(RankInfo rank) {
        return rank.isTeam();
    }

    private CompletableFuture<Optional<String>> answer(Player player, String text, ChatCheck check) {
        switch (check.verdict()) {
            case OK, CAPS_FIXED -> {
                return CompletableFuture.completedFuture(Optional.of(check.text()));
            }
            case MUTED -> {
                return social.get(PunishmentService.class).activeMute(player.getUniqueId()).thenApply(mute -> {
                    mute.ifPresent(punishment -> player.sendMessage(new ModerationTexts(social.get(I18n.class))
                            .muteNotice(social.locale(player), punishment, Instant.now())));
                    return Optional.empty();
                });
            }
            case SPAM_WAIT -> social.send(player, "spam.wait");
            case REPEAT -> social.send(player, "spam.repeat");
            case BURST -> social.send(player, "spam.burst", Social.unparsed("s", seconds(check.retryAfter())));
            case AD -> {
                social.send(player, "spam.ad");
                String server = Social.serverName(player).orElse("-");
                social.core().proxy().getAllPlayers().stream().filter(staff -> staff.hasPermission(STAFF_NOTIFY))
                        .forEach(staff -> social.send(staff, "spam.staff.ad", Social.player(player.getUsername()),
                                Social.unparsed("server", server), Placeholder.unparsed("text", text)));
            }
            default -> throw new IllegalStateException("Unbekanntes Urteil " + check.verdict());
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /** Sekunden aufgerundet, mindestens 1. */
    static long seconds(Duration duration) {
        long millis = duration.toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
