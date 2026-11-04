package net.retromc.skywars.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Globales Event „Chestrefill“: alle Truhen und Fässer wurden neu gefüllt (Spec 04 §6.2.1 Nr. 6, §8.2). */
public final class ChestRefillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int refillNo;

    public ChestRefillEvent(int refillNo) {
        this.refillNo = refillNo;
    }

    /** Stufe des Refills (1, 2 …). */
    public int refillNo() {
        return refillNo;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
