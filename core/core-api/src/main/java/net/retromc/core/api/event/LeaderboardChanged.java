package net.retromc.core.api.event;

import java.util.Objects;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;

/** Bestenliste neu aufgebaut ({@code LEADERBOARD_CHANGED}, net:stats; Spec 02 §8.3): Rollover-Job → alle. */
public record LeaderboardChanged(ModeKey mode, StatPeriod period) {

    public LeaderboardChanged {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(period, "period");
    }
}
