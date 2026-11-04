package net.retromc.game.framework.team;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout der Team-Wahl „Wähle dein Team“ (Spec 03 §5.10): Reihen = 2 + ceil(teams/7), höchstens 6; Positionen
 * 2 → 11, 15 · 3 → 11, 13, 15 · 4 → 10, 12, 14, 16 · 5–7 → ab 10 zentriert · 8 → 9–12, 14–17 · mehr → Reihen à 7 ab
 * 10, 19, 28, 37. „Team verlassen“ liegt in der Mitte der letzten Reihe.
 *
 * @param rows  Reihen
 * @param slots Slot je Team in Reihenfolge
 * @param leave Slot von „Team verlassen“
 */
public record TeamGuiLayout(int rows, List<Integer> slots, int leave) {

    /** Höchstzahl Teams (4 Reihen à 7). */
    public static final int MAX_TEAMS = 28;

    public TeamGuiLayout {
        slots = List.copyOf(slots);
    }

    public static TeamGuiLayout of(int teams) {
        if (teams < 1 || teams > MAX_TEAMS) {
            throw new IllegalArgumentException("1–" + MAX_TEAMS + " Teams: " + teams);
        }
        int rows = Math.min(6, 2 + (teams + 6) / 7);
        List<Integer> slots = switch (teams) {
            case 1 -> List.of(13);
            case 2 -> List.of(11, 15);
            case 3 -> List.of(11, 13, 15);
            case 4 -> List.of(10, 12, 14, 16);
            case 5, 6, 7 -> row(10 + (7 - teams) / 2, teams);
            case 8 -> List.of(9, 10, 11, 12, 14, 15, 16, 17);
            default -> rowsOfSeven(teams);
        };
        return new TeamGuiLayout(rows, slots, (rows - 1) * 9 + 4);
    }

    private static List<Integer> row(int start, int count) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(start + i);
        }
        return slots;
    }

    private static List<Integer> rowsOfSeven(int teams) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            slots.add(10 + (i / 7) * 9 + i % 7);
        }
        return slots;
    }
}
