package net.retromc.bedwars;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * BedWars-Stand eines Spielers in der Runde: Zähler (alle Kills, Final-Kills, Betten, Käufe), gekaufte permanente
 * Stufen (Rüstung, Spitzhacke, Axt, Schere) und die Hotbar-Slots von Schwert/Spitzhacke beim Tod (Spec 04 §6.1.4
 * Nr. 3). In die Statistik gehen nur Final-Kills/-Deaths (§6.1.4 Nr. 7, §9 #10).
 */
public final class BwPlayer {

    /** Rüstungsstufen: 0 Leder, 1 Kette, 2 Eisen, 3 Diamant (nur der Brustpanzer wird ersetzt). */
    public static final int MAX_ARMOR = 3;

    private final UUID uuid;
    private int kills;
    private int finalKills;
    private int beds;
    private boolean finalDeath;
    private int purchases;
    private int armor;
    private int pickaxe = 1;
    private boolean axe;
    private boolean shears;
    private int swordSlot = 0;
    private int pickaxeSlot = 1;
    private final Map<String, Integer> counters = new HashMap<>();

    public BwPlayer(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized void kill(boolean finalKill) {
        kills++;
        if (finalKill) {
            finalKills++;
        }
    }

    public synchronized int kills() {
        return kills;
    }

    public synchronized int finalKills() {
        return finalKills;
    }

    public synchronized void bed() {
        beds++;
    }

    public synchronized int beds() {
        return beds;
    }

    public synchronized void finalDeath() {
        finalDeath = true;
    }

    public synchronized boolean hasFinalDeath() {
        return finalDeath;
    }

    public synchronized void purchase() {
        purchases++;
    }

    public synchronized int purchases() {
        return purchases;
    }

    public synchronized int armor() {
        return armor;
    }

    /** Neue Rüstungsstufe; niedrigere Käufe haben keine Wirkung. */
    public synchronized boolean armor(int tier) {
        if (tier <= armor || tier > MAX_ARMOR) {
            return false;
        }
        armor = tier;
        return true;
    }

    /** Spitzhacken-Stufe: 1 Holz, 2 Eisen, 3 Gold, 4 Diamant (sinkt beim Tod nicht). */
    public synchronized int pickaxe() {
        return pickaxe;
    }

    public synchronized boolean pickaxe(int tier) {
        if (tier <= pickaxe) {
            return false;
        }
        pickaxe = tier;
        return true;
    }

    public synchronized boolean axe() {
        return axe;
    }

    public synchronized void axe(boolean value) {
        axe = value;
    }

    public synchronized boolean shears() {
        return shears;
    }

    public synchronized void shears(boolean value) {
        shears = value;
    }

    public synchronized int swordSlot() {
        return swordSlot;
    }

    public synchronized int pickaxeSlot() {
        return pickaxeSlot;
    }

    /** Merkt die Hotbar-Slots beim Tod (−1 = nicht in der Hotbar, Standard bleibt). */
    public synchronized void slots(int sword, int pick) {
        if (sword >= 0 && sword <= 8) {
            swordSlot = sword;
        }
        if (pick >= 0 && pick <= 8 && pick != swordSlot) {
            pickaxeSlot = pick;
        }
    }

    /** Freier Zähler (Achievement-Fortschritt, Fallen …). */
    public synchronized void count(String key) {
        counters.merge(key, 1, Integer::sum);
    }

    public synchronized int counter(String key) {
        return counters.getOrDefault(key, 0);
    }
}
