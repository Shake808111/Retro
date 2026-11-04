package net.retromc.game.framework.phase;

import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Regeln des Lobby-Countdowns (Spec 03 §6.5, §8.1 {@code CountdownPlan}), ohne Server testbar.
 *
 * <ul>
 *   <li>Länge {@link #total()} (Standard 60 s), Ansagen nur bei den Werten aus {@link #announce(int)}.</li>
 *   <li>Server voll oder {@code /start}: Rest &gt; 10 → 10 s, sonst unverändert.</li>
 *   <li>Zu wenige Spieler: Abbruch zurück auf {@link #total()}, außer ab Rest ≤ 5 s (Schutz).</li>
 *   <li>Teamwahl und {@code /forcemap} sind ab Rest ≤ 10 s gesperrt.</li>
 * </ul>
 */
public final class CountdownPlan {

    /** Standardlänge des Lobby-Countdowns (Spec 03 §6.5, §10 #6). */
    public static final int DEFAULT_TOTAL = 60;
    /** Ansagen des Rahmens (Spec 03 §6.5). */
    public static final Set<Integer> DEFAULT_ANNOUNCE = Set.of(60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1);
    /** Ziel bei vollem Server und {@code /start}. */
    public static final int SHORTENED = 10;
    /** Ab diesem Rest kein Abbruch mehr. */
    public static final int PROTECTION = 5;
    /** Ab diesem Rest sind Teamwahl und Forcemap gesperrt. */
    public static final int LOCK = 10;

    /** Ereignis, das den Rest verändert. */
    public enum Event {
        /** Eine Sekunde vergangen. */
        TICK,
        /** Server voll. */
        FULL,
        /** {@code /start}. */
        START_CMD,
        /** Spielerzahl unter {@code minPlayers}. */
        BELOW_MIN
    }

    private final int total;
    private final SortedSet<Integer> announce;

    private CountdownPlan(int total, Set<Integer> announce) {
        if (total < 1) {
            throw new IllegalArgumentException("total muss ≥ 1 sein");
        }
        this.total = total;
        this.announce = new TreeSet<>(Objects.requireNonNull(announce, "announce"));
    }

    /** Countdown mit Länge und Ansage-Werten. */
    public static CountdownPlan lobby(int total, Set<Integer> announce) {
        return new CountdownPlan(total, announce);
    }

    /** Standard des Rahmens: 60 s, Ansagen 60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1. */
    public static CountdownPlan standard() {
        return new CountdownPlan(DEFAULT_TOTAL, DEFAULT_ANNOUNCE);
    }

    public int total() {
        return total;
    }

    /** Neuer Rest nach {@code event}. */
    public int next(int rest, Event event) {
        return switch (Objects.requireNonNull(event, "event")) {
            case TICK -> Math.max(0, rest - 1);
            case FULL, START_CMD -> rest > SHORTENED ? SHORTENED : rest;
            case BELOW_MIN -> cancels(rest) ? total : rest;
        };
    }

    /** Ob zu wenige Spieler den Countdown bei diesem Rest noch abbrechen (Rest &gt; 5 s). */
    public boolean cancels(int rest) {
        return rest > PROTECTION;
    }

    /** Ob {@code /start} bzw. „voll“ den Countdown verkürzen würde (Rest &gt; 10 s). */
    public boolean shortens(int rest) {
        return rest > SHORTENED;
    }

    /** Ob bei diesem Rest angesagt wird. */
    public boolean announce(int rest) {
        return announce.contains(rest);
    }

    /** Ob Teamwahl und Forcemap noch offen sind (Rest &gt; 10 s). */
    public boolean choicesOpen(int rest) {
        return rest > LOCK;
    }

    /** Fortschritt für den XP-Balken ({@code exp = Rest/Gesamt}). */
    public float progress(int rest) {
        return Math.clamp((float) rest / total, 0f, 1f);
    }

    /** Ansage-Werte absteigend. */
    public SortedSet<Integer> announcements() {
        return java.util.Collections.unmodifiableSortedSet(announce);
    }
}
