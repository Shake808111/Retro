package net.retromc.bedwars.upgrade;

import java.util.List;
import java.util.Locale;

/**
 * Team-Upgrades gegen Diamanten (Spec 04 §6.1.7): Kosten je Level und Slot im Upgrade-Shop (§5.2).
 *
 * @param costs Kosten je Level (Index 0 = Level 1)
 * @param slot  Slot im Upgrade-Shop
 * @param icon  Material
 */
public enum UpgradeType {
    /** Schärfe I/II auf Schwertern und Äxten. */
    WEAPONS(List.of(4, 12), 10, "IRON_SWORD"),
    /** Schutz I–IV. */
    ARMOR(List.of(2, 8, 14, 20), 11, "IRON_CHESTPLATE"),
    /** Effizienz I/II auf Werkzeugen. */
    MINING(List.of(2, 6), 12, "IRON_PICKAXE"),
    /** Federfall I/II auf Schuhen (II = 6, Spec 04 §10 #6). */
    FEATHER(List.of(3, 6), 13, "FEATHER"),
    /** L1 Regeneration I, L2 + Eile I im Basis-Radius 12. */
    BASE(List.of(3, 6), 14, "BEACON"),
    /** Kupfer-/Eisen-Takt −15 % je Level (L5 = −75 %). */
    RESOURCES(List.of(2, 4, 6, 8, 12), 15, "FURNACE");

    /** Verkürzung des Kupfer-/Eisen-Takts je Level. */
    public static final double RESOURCE_STEP = 0.15;

    private final List<Integer> costs;
    private final int slot;
    private final String icon;

    UpgradeType(List<Integer> costs, int slot, String icon) {
        this.costs = costs;
        this.slot = slot;
        this.icon = icon;
    }

    public int maxLevel() {
        return costs.size();
    }

    /** Kosten für {@code level} (1-basiert). */
    public int cost(int level) {
        if (level < 1 || level > costs.size()) {
            throw new IllegalArgumentException("Level 1–" + costs.size() + ": " + level);
        }
        return costs.get(level - 1);
    }

    public int slot() {
        return slot;
    }

    public String icon() {
        return icon;
    }

    /** Textschlüssel des Chatnamens ({@code bw.upgrade.weapons}). */
    public String nameKey() {
        return "bw.upgrade." + name().toLowerCase(Locale.ROOT);
    }

    /** Faktor auf den Takt bei Ressourcen-Level {@code level} (1 − 0,15·n). */
    public static double resourceFactor(int level) {
        return Math.max(0.25, 1.0 - RESOURCE_STEP * Math.clamp(level, 0, RESOURCES.maxLevel()));
    }
}
