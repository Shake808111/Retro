package net.retromc.skywars.board;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Modus-Level im Chat ({@code §7[{lvlColor}{lvl}§7] }) und am Nametag ({@code §f{name} §7[{lvlColor}{level}§7]}),
 * Spec 04 §4.5, §6.2.3: Farbe je Level-Stufe, ab {@code starFrom} mit {@code ✪} (Schwellen Annahme, Konfigwert).
 *
 * @param colors   Farbe ab Level (§-Code)
 * @param starFrom ab diesem Level {@code ✪}
 */
public record LevelTag(Map<Integer, String> colors, int starFrom) {

    /** Stern ab {@code starFrom}. */
    public static final String STAR = "✪";

    public LevelTag {
        colors = Map.copyOf(colors);
    }

    /** §-Farbe eines Levels ({@code §7}, wenn keine Stufe passt). */
    public String color(int level) {
        NavigableMap<Integer, String> sorted = new TreeMap<>(colors);
        Map.Entry<Integer, String> entry = sorted.floorEntry(level);
        return entry == null ? "§7" : entry.getValue();
    }

    /** Text des Levels: Zahl, ab {@code starFrom} mit {@code ✪}. */
    public String text(int level) {
        return level >= starFrom ? level + STAR : Integer.toString(level);
    }
}
