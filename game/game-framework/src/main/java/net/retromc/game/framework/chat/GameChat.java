package net.retromc.game.framework.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jspecify.annotations.Nullable;

/**
 * Chat in der laufenden Runde (Spec 03 §6.8 Nr. 4, Spec 04 §6.1.8): Zuschauer schreiben nur an Zuschauer
 * ({@code common.spectator.chat}); in Teamvarianten geht der Chat ans eigene Team ({@code game.chat.team}), mit
 * {@code @a}/{@code @all} an alle ({@code game.chat.all}); in 1er-Varianten an alle. Läuft nach dem Chat-Schutz
 * (Mute/Spam, niedrigere Priorität) und ersetzt das Standardformat; Wartelobby und ENDING bleiben beim Kern-Chat
 * (farbiges GL/GG).
 */
public final class GameChat implements Listener {

    private final Game game;

    public GameChat(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (game.state() != GameState.INGAME) {
            return;
        }
        Player sender = event.getPlayer();
        UUID uuid = sender.getUniqueId();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean alive = game.player(uuid).map(GamePlayer::alive).orElse(false);
        String key;
        Predicate<Player> recipients;
        if (!alive) {
            key = "common.spectator.chat";
            recipients = viewer -> !game.player(viewer.getUniqueId()).map(GamePlayer::alive).orElse(false);
        } else if (game.variant().teamPlay() && game.teams().teamOf(uuid) != null) {
            String stripped = stripAll(text);
            if (stripped != null) {
                if (stripped.isBlank()) {
                    event.setCancelled(true);
                    return;
                }
                text = stripped;
                key = "game.chat.all";
                recipients = viewer -> true;
            } else {
                GameTeam team = game.teams().teamOf(uuid);
                key = "game.chat.team";
                recipients = viewer -> team != null && team.contains(viewer.getUniqueId());
            }
        } else {
            key = "game.chat.team";
            recipients = viewer -> true;
        }
        event.viewers().removeIf(viewer -> viewer instanceof Player player && !player.equals(sender)
                && !recipients.test(player));
        GameTexts texts = game.texts();
        String message = text;
        String resolved = texts.key(key);
        event.renderer((source, displayName, ignored, viewer) -> {
            Locale locale = viewer instanceof Player player ? texts.locale(player) : Locale.GERMANY;
            return texts.i18n().render(locale, resolved, texts.name("name", sender),
                    Placeholder.component("clan", texts.clan(locale, uuid)), Placeholder.unparsed("msg", message),
                    Placeholder.unparsed("message", message));
        });
    }

    /** Text ohne {@code @a }/{@code @all } oder {@code null}, wenn kein Präfix. */
    static @Nullable String stripAll(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : new String[] {"@all", "@a"}) {
            if (lower.equals(prefix)) {
                return "";
            }
            if (lower.startsWith(prefix + " ")) {
                return text.substring(prefix.length() + 1).strip();
            }
        }
        return null;
    }
}
