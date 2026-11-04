package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GAME_ENDED {matchId, mode, variant, map, winners[], durationS, replayId?, players[]}} (net:game; Spec 03
 * §8.4): Spielserver → Stats-Jobs, Replay-Dienst, Lobby ({@code replay:last}), Discord.
 */
public record GameEnded(String matchId, String mode, String variant, String map, List<UUID> winners, long durationS,
        @Nullable String replayId, List<UUID> players) {

    public GameEnded {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(map, "map");
        winners = List.copyOf(winners);
        players = List.copyOf(players);
        if (durationS < 0) {
            throw new IllegalArgumentException("durationS < 0");
        }
    }
}
