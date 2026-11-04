package net.retromc.game.framework.team;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Teamfarben (Spec 03 §6.6, Spec 04 §6.1.1). {@code color} ist ein §-Code oder {@code #RRGGBB}, {@code dye} die
 * Blockfarbe ({@code {dye}_wool}, {@code {dye}_terracotta}, {@code {dye}_stained_glass}). Die Anzeigenamen kommen
 * aus {@code team.name.<id>}.
 */
public enum TeamColor {
    RED("red", "§c", "RED"),
    BLUE("blue", "§9", "BLUE"),
    GREEN("green", "§a", "LIME"),
    YELLOW("yellow", "§e", "YELLOW"),
    AQUA("aqua", "§b", "LIGHT_BLUE"),
    PURPLE("purple", "§5", "PURPLE"),
    PINK("pink", "§d", "PINK"),
    ORANGE("orange", "§6", "ORANGE"),
    WHITE("white", "§f", "WHITE"),
    GRAY("gray", "§7", "GRAY"),
    /** BedWars Türkis (Lotus). */
    CYAN("cyan", "§3", "CYAN"),
    /** BedWars Magenta, Hex wegen Kollision mit Rosa (Spec 04 §10 #18). */
    MAGENTA("magenta", "#C040C0", "MAGENTA"),
    /** BedWars Braun (Spec 04 §10 #18). */
    BROWN("brown", "#8B5A2B", "BROWN"),
    /** BedWars Dunkelgrün. */
    DARK_GREEN("dark_green", "§2", "GREEN");

    /** Reihenfolge des Rahmens (Spec 03 §6.6, Nr. 1–8; Weiß/Grau Reserve). */
    public static final List<TeamColor> FRAME_ORDER =
            List.of(RED, BLUE, GREEN, YELLOW, AQUA, PURPLE, PINK, ORANGE, WHITE, GRAY);

    /** Reihenfolge BedWars (Spec 04 §6.1.1, Index 0–11). */
    public static final List<TeamColor> BEDWARS_ORDER =
            List.of(RED, BLUE, YELLOW, GREEN, PINK, AQUA, ORANGE, PURPLE, CYAN, MAGENTA, BROWN, DARK_GREEN);

    private final String id;
    private final String color;
    private final String dye;

    TeamColor(String id, String color, String dye) {
        this.id = id;
        this.color = color;
        this.dye = dye;
    }

    /** Team-ID ({@code map.json}, {@code /team <farbe>}, Textschlüssel). */
    public String id() {
        return id;
    }

    /** Chat-/Tab-Farbe als §-Code oder Hex. */
    public String color() {
        return color;
    }

    /** {@code DyeColor}-Name der Blockfarbe. */
    public String dye() {
        return dye;
    }

    /** Material der Wolle ({@code red_wool} …). */
    public String wool() {
        return dye.toLowerCase(Locale.ROOT) + "_wool";
    }

    /** Textschlüssel des Teamnamens. */
    public String nameKey() {
        return "team.name." + id;
    }

    public static Optional<TeamColor> byId(String id) {
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(color -> color.id.equals(wanted)).findFirst();
    }
}
