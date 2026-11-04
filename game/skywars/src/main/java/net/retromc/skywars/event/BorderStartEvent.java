package net.retromc.skywars.event;

import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Die Border beginnt zu schrumpfen (Spec 04 §6.2.1 Nr. 7, §8.2). */
public final class BorderStartEvent extends Event {

    /** Auslöser. */
    public enum Cause {
        /** Kampf + 600 s. */
        TIME,
        /** 60 s nach „nur noch 2 Teams“. */
        TWO_TEAMS
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Cause cause;
    private final int durationS;

    public BorderStartEvent(Cause cause, int durationS) {
        this.cause = Objects.requireNonNull(cause, "cause");
        this.durationS = durationS;
    }

    public Cause cause() {
        return cause;
    }

    /** Dauer des Schrumpfens in Sekunden. */
    public int durationS() {
        return durationS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
