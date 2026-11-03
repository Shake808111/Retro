package net.retromc.paper.core.event;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Sprache geändert ({@code /language}, Spec 02 §6.13 Nr. 3, §8.2): Tab, Scoreboard und Hologramme rendern neu. */
public final class LocaleChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Locale locale;

    public LocaleChangedEvent(Player player, Locale locale) {
        this.player = Objects.requireNonNull(player, "player");
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    /** Spieler. */
    public Player player() {
        return player;
    }

    /** Neue Sprache. */
    public Locale locale() {
        return locale;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
