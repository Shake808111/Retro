package net.retromc.bedwars.trap;

import java.util.Locale;

/**
 * Fallen (Spec 04 §6.1.7): Kosten in Diamanten, Radius (Abbaulähmung verkleinert auf 6), Wirkung bei Eindringen
 * eines Gegners. Werte ohne Beleg sind Annahmen (Konfigwerte).
 *
 * @param cost    Diamanten
 * @param radius  Auslöseradius in Blöcken um das Bett
 * @param icon    Material im Upgrade-Shop
 * @param seconds Wirkdauer
 */
public enum TrapType {
    LIGHTNING(1, 10, "LIGHTNING_ROD", 10),
    BLINDNESS(1, 10, "INK_SAC", 8),
    WEAKNESS(2, 10, "FERMENTED_SPIDER_EYE", 60),
    REVEAL(1, 10, "SPYGLASS", 15),
    MINING_FATIGUE(1, 6, "IRON_PICKAXE", 10);

    private final int cost;
    private final int radius;
    private final String icon;
    private final int seconds;

    TrapType(int cost, int radius, String icon, int seconds) {
        this.cost = cost;
        this.radius = radius;
        this.icon = icon;
        this.seconds = seconds;
    }

    public int cost() {
        return cost;
    }

    public int radius() {
        return radius;
    }

    public String icon() {
        return icon;
    }

    public int seconds() {
        return seconds;
    }

    /** Textschlüssel des Namens ({@code bw.trap.lightning}). */
    public String nameKey() {
        return "bw.trap." + name().toLowerCase(Locale.ROOT);
    }
}
