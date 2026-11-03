package net.retromc.paper.core.bar;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Bossbars (Spec 02 §4.5, §8.1 {@code BossbarService}): höchstens eine je Kanal, angezeigt wird die des wichtigsten
 * Kanals: {@code game} > {@code event} > {@code season} > {@code news}.
 */
public interface BossbarService {

    String GAME = "game";
    String EVENT = "event";
    String SEASON = "season";
    String NEWS = "news";
    /** Reihenfolge der Anzeige, wichtigster Kanal zuerst. */
    List<String> PRIORITY = List.of(GAME, EVENT, SEASON, NEWS);

    void show(Player player, String channel, BossBarSpec spec);

    void hide(Player player, String channel);

    /** Kanal, dessen Bossbar angezeigt wird; unbekannte Kanäle stehen hinter {@code news}. */
    static Optional<String> active(Collection<String> channels) {
        for (String channel : PRIORITY) {
            if (channels.contains(channel)) {
                return Optional.of(channel);
            }
        }
        return channels.stream().sorted().findFirst();
    }
}
