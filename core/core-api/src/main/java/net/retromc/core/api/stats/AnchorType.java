package net.retromc.core.api.stats;

/** Art eines Anzeige-Ankers ({@code core.display_anchor.type}, Spec 02 §4.9). */
public enum AnchorType {
    /** Schilderwand: Schilder nach rechts mit Spielerkopf darüber. */
    SIGN_WALL,
    /** Hologramm mit der Bestenliste, für alle sichtbar. */
    HOLO_TOP,
    /** Hologramm mit den eigenen Werten, nur für den Betrachter. */
    HOLO_PERSONAL
}
