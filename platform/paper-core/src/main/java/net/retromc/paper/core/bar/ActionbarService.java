package net.retromc.paper.core.bar;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Actionbar (Spec 02 §4.6, §8.1 {@code ActionbarService}): Kanäle mit Priorität {@code temporary} (Dauer in Ticks) >
 * {@code cooldown} > {@code persistent} > {@code ticker}. Der aktuelle Text wird alle 40 Ticks neu gesendet, weil
 * Vanilla die Actionbar nach etwa 3 s ausblendet.
 */
public interface ActionbarService {

    /** Erneutes Senden in Ticks. */
    int RESEND_TICKS = 40;

    /**
     * Setzt den Text eines Kanals.
     *
     * @param durationTicks Anzeigedauer; 0 = bis {@link #clear}
     */
    void set(Player player, String channel, Priority priority, Component text, int durationTicks);

    void clear(Player player, String channel);

    /** Nachrichten des Lobby-Newstickers (ersetzt {@code ui:news}, solange die Liste nicht leer ist). */
    void ticker(List<NewsItem> items);

    /** Priorität, wichtigste zuerst. */
    enum Priority {
        TEMPORARY,
        COOLDOWN,
        PERSISTENT,
        TICKER
    }
}
