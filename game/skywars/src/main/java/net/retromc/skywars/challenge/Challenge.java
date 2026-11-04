package net.retromc.skywars.challenge;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Rundenchallenges (Spec 04 §6.2.6, je Runde eine zufällig): Text {@code sw.challenge.<id>}, Bedingung und XP
 * (Standard, überschreibbar in {@code challenges.yml}).
 */
public enum Challenge {
    /** Sieg ohne Schwerttreffer/-halten im Kampf. */
    NO_SWORD("no_sword", true, 0, 50),
    /** 3 Kills. */
    KILLS_3("kills_3", false, 3, 50),
    /** Sieg ohne Rüstung. */
    NO_ARMOR("no_armor", true, 0, 50),
    /** 10 Truhen öffnen. */
    CHESTS_10("chests_10", false, 10, 30),
    /** Sieg, ohne die Mitte zu betreten. */
    NO_MID("no_mid", true, 0, 50);

    private final String id;
    private final boolean needsWin;
    private final int target;
    private final int defaultXp;

    Challenge(String id, boolean needsWin, int target, int defaultXp) {
        this.id = id;
        this.needsWin = needsWin;
        this.target = target;
        this.defaultXp = defaultXp;
    }

    public String id() {
        return id;
    }

    /** Verlangt den Rundensieg (sonst ein Zählziel). */
    public boolean needsWin() {
        return needsWin;
    }

    /** Zählziel (Kills, Truhen) oder 0. */
    public int target() {
        return target;
    }

    public int defaultXp() {
        return defaultXp;
    }

    /** Textschlüssel der Challenge. */
    public String textKey() {
        return "sw.challenge." + id;
    }

    public static Optional<Challenge> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id.toLowerCase(Locale.ROOT))).findFirst();
    }
}
