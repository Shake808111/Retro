package net.retromc.bedwars.board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Score-Logik des BedWars-Ingame-Boards (Spec 04 §4.2, §9 #1, U2): Kopfzeilen tragen die Scores
 * {@code maxTeamSize+6 … maxTeamSize+1} (8x1 → 7..2, 4x4 → 10..5, 9x5 → 11..6), Teamzeilen die Zahl der lebenden
 * Spieler. Sortierung wie Vanilla (Score absteigend); Gleichstand: eigenes Team zuerst, dann feste Teamreihenfolge.
 */
public final class BoardScores {

    /** Anzahl der Kopfzeilen (Leer, Map, Leer, Diamant, Gold, Leer). */
    public static final int HEADER_LINES = 6;

    /** Teamzeile vor dem Sortieren. */
    public record TeamLine(int index, int alive, boolean bed) {
    }

    private BoardScores() {
    }

    /** Scores der Kopfzeilen von oben nach unten. */
    public static List<Integer> header(int maxTeamSize) {
        List<Integer> scores = new ArrayList<>();
        for (int i = HEADER_LINES; i >= 1; i--) {
            scores.add(maxTeamSize + i);
        }
        return scores;
    }

    /** Teamzeilen in Anzeigereihenfolge für den Betrachter mit Team {@code ownIndex} (−1 = ohne Team). */
    public static List<TeamLine> sort(List<TeamLine> teams, int ownIndex) {
        List<TeamLine> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparingInt(TeamLine::alive).reversed()
                .thenComparing(line -> line.index() == ownIndex ? 0 : 1)
                .thenComparingInt(TeamLine::index));
        return sorted;
    }
}
