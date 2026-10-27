package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.stats.ModeKey;

/** Neue Statistik nach Rundenende ({@code STATS_UPDATED}, net:stats; Spec 02 §8.3): Spielserver → Lobbys (Wände, Hologramme). */
public record StatsUpdated(ModeKey mode, List<UUID> uuids) {

    public StatsUpdated {
        Objects.requireNonNull(mode, "mode");
        uuids = List.copyOf(uuids);
    }
}
