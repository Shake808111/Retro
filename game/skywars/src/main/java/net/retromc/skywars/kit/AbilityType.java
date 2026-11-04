package net.retromc.skywars.kit;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Fähigkeiten der SkyWars-Kits (Spec 04 §6.2.5; Wirkungen überwiegend Annahme, Werte in {@code kits/skywars.yml}). */
public enum AbilityType {
    /** Engel: pro Kill 1 Goldener Apfel (passiv). */
    ANGEL(false),
    /** Jäger: +1 Extraherz je Kill, max. +5 (passiv). */
    HUNTER(false),
    /** Goldsucher: Erz-Abbau gibt doppelt (passiv). */
    GOLD_DIGGER(false),
    /** Stampfer: Landung aus ≥ 4 Blöcken erzeugt eine Schockwelle (ausgelöst, mit Abklingzeit). */
    STOMPER(false),
    /** Teufel: Plagegeist beschwören. */
    DEVIL(true),
    /** Phantom: Flug-Dash. */
    PHANTOM(true),
    /** Ziege: Rammstoß. */
    GOAT(true),
    /** Ninja: Unsichtbarkeit. */
    NINJA(true),
    /** Poseidon: Wasserwelle. */
    POSEIDON(true),
    /** Wolke: Wolkenplattform unter dir. */
    CLOUD(true),
    /** Hundeflüsterer: zahmer Wolf je Spawnei. */
    DOG_WHISPERER(true),
    /** Luftbändiger: Windstoß. */
    AIRBENDER(true),
    /** Nachtmagier: Blindheit an Gegner. */
    NIGHT_MAGE(true),
    /** Bienen: Bienenschwarm. */
    BEES(true);

    private final boolean active;

    AbilityType(boolean active) {
        this.active = active;
    }

    /** Wird mit Rechtsklick auf das Fähigkeits-Item ausgelöst. */
    public boolean active() {
        return active;
    }

    public static Optional<AbilityType> byId(String id) {
        String upper = id.toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(type -> type.name().equals(upper)).findFirst();
    }
}
