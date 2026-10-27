package net.retromc.core.api.social;

/** Wer einer öffentlichen Party beitreten darf ({@code party:{id}.publicFilter}, Spec 01 §3.3 #34, §6.5 Nr. 6). */
public enum PublicFilter {
    /** Alle Spieler. */
    ALL,
    /** Nur Premium und höher (Gewicht ≥ 200). */
    PREMIUM;

    public boolean admits(int rankWeight) {
        return this == ALL || rankWeight >= PrivacySettings.PREMIUM_WEIGHT;
    }
}
