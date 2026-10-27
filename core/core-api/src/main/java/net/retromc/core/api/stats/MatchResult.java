package net.retromc.core.api.stats;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis einer Runde für {@code StatsService.submit} (Spec 02 §6.4). Idempotent über {@code matchId}
 * ({@code INSERT stats.match … ON CONFLICT DO NOTHING}).
 *
 * @param variant Variante, z. B. {@code 4x4} ({@code stats.match.variant})
 * @param server Servername ({@code stats.match.server})
 * @param winnerTeam Siegerteam oder {@code null}
 */
public record MatchResult(
        UUID matchId,
        ModeKey mode,
        String variant,
        String map,
        String server,
        Instant startedAt,
        Instant endedAt,
        @Nullable Integer winnerTeam,
        List<PlayerMatchStats> players) {

    public MatchResult {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        players = List.copyOf(players);
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt liegt vor startedAt");
        }
        Set<UUID> seen = new HashSet<>();
        for (PlayerMatchStats player : players) {
            if (!seen.add(player.uuid())) {
                throw new IllegalArgumentException("Spieler doppelt im Ergebnis: " + player.uuid());
            }
        }
    }

    public Optional<PlayerMatchStats> player(UUID uuid) {
        return players.stream().filter(player -> player.uuid().equals(uuid)).findFirst();
    }
}
