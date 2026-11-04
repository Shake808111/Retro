package net.retromc.game.framework.end;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.team.GameTeam;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis einer Runde (Spec 03 §8.1 {@code GameResult}): Siegerteam oder Sieger, Platzierungen, Unentschieden
 * oder Abbruch. Rundenblock und Basis-Coins liefert das Modul über {@code GameMode}.
 *
 * @param winnerTeam Siegerteam (Teammodi) oder {@code null}
 * @param winner     Sieger (Solo-Modi) oder {@code null}
 * @param placements Platzierungen, Platz 1 zuerst (optional)
 * @param draw       Zeitlimit ohne Sieger ({@code common.draw})
 * @param aborted    alle weg in INGAME: ohne Rundenblock, Restart 5 s
 */
public record GameResult(@Nullable GameTeam winnerTeam, @Nullable UUID winner, List<Placement> placements,
        boolean draw, boolean aborted) {

    /** Platz eines Spielers. */
    public record Placement(UUID player, int place) {

        public Placement {
            Objects.requireNonNull(player, "player");
            if (place < 1) {
                throw new IllegalArgumentException("Platz ≥ 1");
            }
        }
    }

    public GameResult {
        placements = List.copyOf(placements);
    }

    public static GameResult ofTeam(GameTeam team) {
        return new GameResult(Objects.requireNonNull(team, "team"), null, List.of(), false, false);
    }

    public static GameResult ofPlayer(UUID winner, List<Placement> placements) {
        return new GameResult(null, Objects.requireNonNull(winner, "winner"), placements, false, false);
    }

    public static GameResult ofDraw() {
        return new GameResult(null, null, List.of(), true, false);
    }

    public static GameResult ofAbort() {
        return new GameResult(null, null, List.of(), false, true);
    }

    /** Ob {@code player} gewonnen hat. */
    public boolean won(GamePlayer player) {
        if (winner != null) {
            return winner.equals(player.uuid());
        }
        GameTeam team = winnerTeam;
        return team != null && player.team() == team;
    }

    /** Platz eines Spielers, falls vergeben. */
    public Optional<Integer> placeOf(UUID player) {
        return placements.stream().filter(p -> p.player().equals(player)).map(Placement::place).findFirst();
    }
}
