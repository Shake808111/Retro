package net.retromc.game.framework;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.game.framework.team.GameTeam;
import org.jspecify.annotations.Nullable;

/**
 * Teilnehmer einer Runde mit den Zählern für {@code StatsService.submit} (Spec 02 §6.4): Kills, Deaths, XP, Punkte
 * und modusspezifische Werte ({@code beds}, {@code chests} …). Die Zähler pflegt das Modul.
 */
public final class GamePlayer {

    /** Zustand in der Runde. */
    public enum State {
        /** Spielt (lebt). */
        PLAYING,
        /** Ausgeschieden, schaut zu. */
        SPECTATOR,
        /** Hat den Server während der Runde verlassen. */
        LEFT
    }

    private final UUID uuid;
    private final String name;
    private final Instant joinedAt;
    private final Map<String, Long> extra = new ConcurrentHashMap<>();
    private volatile State state = State.PLAYING;
    private volatile @Nullable GameTeam team;
    private volatile boolean participant;
    private volatile int kills;
    private volatile int deaths;
    private volatile int xp;
    private volatile @Nullable Long points;

    public GamePlayer(UUID uuid, String name, Instant joinedAt) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public State state() {
        return state;
    }

    public void state(State value) {
        this.state = Objects.requireNonNull(value, "value");
    }

    /** Lebt und spielt. */
    public boolean alive() {
        return state == State.PLAYING && participant;
    }

    /** War beim Rundenstart dabei (zählt für Statistik und Coins). */
    public boolean participant() {
        return participant;
    }

    public void participant(boolean value) {
        this.participant = value;
    }

    public @Nullable GameTeam team() {
        return team;
    }

    public void team(@Nullable GameTeam value) {
        this.team = value;
    }

    public int kills() {
        return kills;
    }

    public synchronized void addKill() {
        kills++;
    }

    public int deaths() {
        return deaths;
    }

    public synchronized void addDeath() {
        deaths++;
    }

    public int xp() {
        return xp;
    }

    public synchronized void addXp(int amount) {
        xp += Math.max(0, amount);
    }

    /** Vom Modul gesetzte Punkte oder {@code null} (dann rechnet core-stats nach der Formel). */
    public @Nullable Long points() {
        return points;
    }

    public void points(@Nullable Long value) {
        this.points = value;
    }

    /** Modusspezifischer Zähler (0, wenn nicht gesetzt). */
    public long extra(String key) {
        return extra.getOrDefault(key, 0L);
    }

    public void addExtra(String key, long amount) {
        extra.merge(key, amount, Long::sum);
    }

    public Map<String, Long> extras() {
        return Map.copyOf(extra);
    }
}
