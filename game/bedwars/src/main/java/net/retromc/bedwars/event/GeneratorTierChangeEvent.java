package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.bedwars.spawner.Resource;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Neue Generatorstufe (Board, Chat). */
public final class GeneratorTierChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Resource resource;
    private final int tier;
    private final boolean max;

    public GeneratorTierChangeEvent(Resource resource, int tier, boolean max) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.tier = tier;
        this.max = max;
    }

    public Resource resource() {
        return resource;
    }

    public int tier() {
        return tier;
    }

    public boolean max() {
        return max;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
