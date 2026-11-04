package net.retromc.game.framework.kill;

import org.jspecify.annotations.Nullable;

/**
 * Bausteine einer Kill-Meldung (Spec 03 §6.9, §8.1 {@code KillOptions(points, xp, kit, toVictimOnly)}).
 *
 * @param points        Punkte für den Killer ({@code common.kill.self}); 0 = keine Zeile, außer {@code xp} &gt; 0
 * @param xp            XP für den Killer; &gt; 0 hängt {@code common.kill.xp} an die Rundsendung (Ära 26) bzw.
 *                      geht in die Killer-Zeile
 * @param kit           Kit des Killers ({@code common.kit_reveal} an das Opfer) oder {@code null}
 * @param toVictimOnly  TTT-Stil: keine Rundsendung, nur {@code common.killed_by} an das Opfer
 * @param victimMessage das Opfer erhält {@code common.killed_by} statt der Rundsendung
 * @param remaining     verbleibende Spieler für {@code common.remaining} oder −1 (keine Zeile)
 * @param xpInBroadcast XP-Suffix an der Rundsendung statt in der Killer-Zeile
 * @param xpKillerOnly  XP-Suffix der Rundsendung nur in der Ansicht des Killers (SkyWars {@code sw.kill}, Spec 04
 *                      §7.2)
 * @param victimKit     Kit des Opfers ({@code common.kill.kit} an alle, SkyWars {@code sw.kill.kit}) oder {@code null}
 */
public record KillOptions(int points, int xp, @Nullable String kit, boolean toVictimOnly, boolean victimMessage,
        int remaining, boolean xpInBroadcast, boolean xpKillerOnly, @Nullable String victimKit) {

    /** Nur Rundsendung. */
    public static final KillOptions PLAIN = new KillOptions(0, 0, null, false, false, -1, false);

    /** Ohne {@code xpKillerOnly} und {@code victimKit}. */
    public KillOptions(int points, int xp, @Nullable String kit, boolean toVictimOnly, boolean victimMessage,
            int remaining, boolean xpInBroadcast) {
        this(points, xp, kit, toVictimOnly, victimMessage, remaining, xpInBroadcast, false, null);
    }

    public KillOptions withRemaining(int value) {
        return new KillOptions(points, xp, kit, toVictimOnly, victimMessage, value, xpInBroadcast, xpKillerOnly,
                victimKit);
    }
}
