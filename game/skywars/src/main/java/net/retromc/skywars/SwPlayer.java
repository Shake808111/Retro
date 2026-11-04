package net.retromc.skywars;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.retromc.skywars.challenge.ChallengeTracker;
import net.retromc.skywars.kit.AbilityTracker;
import net.retromc.skywars.kit.KitDef;
import org.jspecify.annotations.Nullable;

/** Rundenstand eines SkyWars-Spielers: Kit, Fähigkeit, Zähler für den Rundenblock, Challenge (Main-Thread). */
public final class SwPlayer {

    private final UUID uuid;
    private @Nullable KitDef kit;
    private @Nullable AbilityTracker ability;
    private @Nullable ChallengeTracker challenge;
    private final Set<Long> openedChests = new HashSet<>();
    private int chestsOpened;
    private int blocksPlaced;
    private int extraHearts;
    private long diedAtSecond = -1;
    private boolean inMid;

    public SwPlayer(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    public UUID uuid() {
        return uuid;
    }

    public @Nullable KitDef kit() {
        return kit;
    }

    /** Setzt das Kit der Runde und legt den Fähigkeits-Zähler an. */
    public void kit(KitDef value) {
        this.kit = value;
        KitDef.Ability def = value.ability();
        this.ability = def == null ? null : new AbilityTracker(def.uses(), def.cooldown());
    }

    public @Nullable AbilityTracker ability() {
        return ability;
    }

    public @Nullable ChallengeTracker challenge() {
        return challenge;
    }

    public void challenge(ChallengeTracker value) {
        this.challenge = value;
    }

    /** Zählt einen Container (je Container einmal bis zum nächsten Refill); {@code true} = neu geöffnet. */
    public boolean openChest(long key) {
        if (openedChests.add(key)) {
            chestsOpened++;
            return true;
        }
        return false;
    }

    /** Nach einem Refill zählen alle Container wieder. */
    public void resetOpened() {
        openedChests.clear();
    }

    public int chestsOpened() {
        return chestsOpened;
    }

    public void blockPlaced() {
        blocksPlaced++;
    }

    public int blocksPlaced() {
        return blocksPlaced;
    }

    /** Jäger: Extraherzen (max. {@code max}); {@code true}, wenn eines dazukam. */
    public boolean addHeart(int max) {
        if (extraHearts >= max) {
            return false;
        }
        extraHearts++;
        return true;
    }

    public int extraHearts() {
        return extraHearts;
    }

    /** Rundensekunde des Todes oder −1. */
    public long diedAtSecond() {
        return diedAtSecond;
    }

    public void died(long second) {
        this.diedAtSecond = second;
    }

    public boolean inMid() {
        return inMid;
    }

    public void inMid(boolean value) {
        this.inMid = value;
    }
}
