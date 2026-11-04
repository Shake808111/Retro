package net.retromc.bedwars.event;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.retromc.bedwars.spawner.Resource;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Ressourcen wechseln den Besitzer (Kill, Verlassen, Team-Truhe). */
public final class ResourceTransferEvent extends Event {

    /** Anlass der Übertragung. */
    public enum Cause {
        KILL, LEAVE, CHEST
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID from;
    private final UUID to;
    private final Map<Resource, Integer> amounts;
    private final Cause cause;

    public ResourceTransferEvent(UUID from, UUID to, Map<Resource, Integer> amounts, Cause cause) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.amounts = Map.copyOf(amounts);
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public UUID from() {
        return from;
    }

    public UUID to() {
        return to;
    }

    public Map<Resource, Integer> amounts() {
        return amounts;
    }

    public Cause cause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
