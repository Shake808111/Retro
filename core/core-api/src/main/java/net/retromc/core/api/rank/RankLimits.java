package net.retromc.core.api.rank;

/**
 * Zahlen-Vorteile eines Rangs (Spec 01 §6.2), überschreibbar per LuckPerms-Meta ({@code meta.party-limit},
 * {@code meta.friend-limit}, {@code meta.clan-slot-bonus}, {@code meta.joinme-per-day}, {@code meta.forcemap-per-day}).
 *
 * @param partySize Party-Größe inklusive Leiter (Rang des Leiters zählt, Spec 01 O2)
 * @param friends Freunde-Limit
 * @param clanSlotBonus Bonus auf die Clan-Basisgröße 30 (Rang des Leiters, Spec 01 O3)
 * @param joinmePerDay {@code /joinme} je Tag, {@link #UNLIMITED} = unbegrenzt
 * @param forcemapPerDay {@code /forcemap} je Tag (Spiel-Rahmen), {@link #UNLIMITED} = unbegrenzt
 */
public record RankLimits(int partySize, int friends, int clanSlotBonus, int joinmePerDay, int forcemapPerDay) {

    /** Kennzeichnung „∞“ in der Tabelle. */
    public static final int UNLIMITED = -1;

    public RankLimits {
        if (partySize < 1 || friends < 0 || clanSlotBonus < 0) {
            throw new IllegalArgumentException("Ungültige Rang-Limits");
        }
        if (joinmePerDay < UNLIMITED || forcemapPerDay < UNLIMITED) {
            throw new IllegalArgumentException("Tageslimits müssen ≥ 0 oder UNLIMITED sein");
        }
    }

    /** Ob nach {@code used} Nutzungen eine weitere erlaubt ist. */
    public static boolean allows(int limitPerDay, int used) {
        return limitPerDay == UNLIMITED || used < limitPerDay;
    }
}
