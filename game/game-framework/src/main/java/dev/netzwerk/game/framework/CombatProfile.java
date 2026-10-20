package dev.netzwerk.game.framework;

/**
 * Kampfverhalten eines Modus. Notiz 07, Abschnitt 1: Kampf hinter einer Schnittstelle halten
 * (Angriffs-Cooldown, Rückstoß, Hitbox, Schwertblock, Regeneration), damit OldCombatMechanics
 * später durch eine eigene Umsetzung ersetzt werden kann. Bisher nur die beiden Grundprofile.
 */
public enum CombatProfile {
    /** Vanilla-Kampf mit Angriffs-Cooldown (1.9+). */
    MODERN,
    /** 1.8-artiger Kampf ohne Cooldown, umgesetzt über OldCombatMechanics oder eigenes Modul. */
    LEGACY_1_8
}
