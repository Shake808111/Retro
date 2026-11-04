package net.retromc.skywars.challenge;

import java.util.Objects;

/**
 * Stand der Runden-Challenge eines Spielers (Spec 04 §6.2.6, ohne Server testbar): Scheitern wird einmal sofort
 * gemeldet ({@code sw.challenge.failed}), der Erfolg am Rundenende ({@code sw.challenge.done}).
 */
public final class ChallengeTracker {

    /** Was ein Ereignis bewirkt hat. */
    public enum Change {
        /** Nichts (schon gescheitert oder ohne Bedeutung). */
        NONE,
        /** Zähler gestiegen. */
        PROGRESS,
        /** Gerade gescheitert: jetzt melden. */
        FAILED
    }

    private final Challenge challenge;
    private boolean failed;
    private int progress;

    public ChallengeTracker(Challenge challenge) {
        this.challenge = Objects.requireNonNull(challenge, "challenge");
    }

    public Challenge challenge() {
        return challenge;
    }

    public boolean failed() {
        return failed;
    }

    public int progress() {
        return progress;
    }

    /** Schwert benutzt (Treffer oder in der Hand gehalten) während des Kampfes. */
    public Change swordUsed() {
        return challenge == Challenge.NO_SWORD ? fail() : Change.NONE;
    }

    /** Rüstung angelegt. */
    public Change armorWorn() {
        return challenge == Challenge.NO_ARMOR ? fail() : Change.NONE;
    }

    /** Mitte-Region betreten. */
    public Change midEntered() {
        return challenge == Challenge.NO_MID ? fail() : Change.NONE;
    }

    public Change kill() {
        return challenge == Challenge.KILLS_3 ? count() : Change.NONE;
    }

    public Change chestOpened() {
        return challenge == Challenge.CHESTS_10 ? count() : Change.NONE;
    }

    /**
     * Tod des Spielers: Zählziele sind damit verfehlt; Sieg-Challenges nur, wenn das eigene Team nicht mehr
     * gewinnen kann (Solo, letzter Lebender des Teams).
     */
    public Change died(boolean teamCanStillWin) {
        if (failed) {
            return Change.NONE;
        }
        if (challenge.needsWin()) {
            return teamCanStillWin ? Change.NONE : fail();
        }
        return reached() ? Change.NONE : fail();
    }

    /** Ergebnis am Rundenende. */
    public boolean success(boolean won) {
        if (failed) {
            return false;
        }
        return challenge.needsWin() ? won : reached();
    }

    /** Anteil für die Bossbar bei Zählzielen (0–1), sonst {@code -1}. */
    public float share() {
        return challenge.target() <= 0 ? -1f : Math.min(1f, progress / (float) challenge.target());
    }

    private boolean reached() {
        return progress >= challenge.target();
    }

    private Change count() {
        if (failed) {
            return Change.NONE;
        }
        progress++;
        return Change.PROGRESS;
    }

    private Change fail() {
        if (failed) {
            return Change.NONE;
        }
        failed = true;
        return Change.FAILED;
    }
}
