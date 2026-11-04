package net.retromc.bedwars.spawner;

import java.util.Locale;
import java.util.Optional;

/**
 * Ressourcen (Spec 04 §6.1.3): Kupfer, Eisen, Gold, Diamant; Classic Bronze. {@code cap} = Stapel-Obergrenze am
 * Spawner (Gold/Diamant spawnen nicht weiter, wenn so viele Items im Radius 1,5 liegen).
 */
public enum Resource {
    COPPER("COPPER_INGOT", "§c", 64),
    IRON("IRON_INGOT", "§f", 16),
    GOLD("GOLD_INGOT", "§6", 8),
    DIAMOND("DIAMOND", "§b", 4),
    /** Classic (E17). */
    BRONZE("BRICK", "§c", 64);

    private final String material;
    private final String color;
    private final int cap;

    Resource(String material, String color, int cap) {
        this.material = material;
        this.color = color;
        this.cap = cap;
    }

    /** Bukkit-Material des Items. */
    public String material() {
        return material;
    }

    public String color() {
        return color;
    }

    public int cap() {
        return cap;
    }

    /** {@code copper} … (Konfiguration, Textschlüssel). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Textschlüssel des Namens ({@code bw.resource.copper}); Diamant mit Mehrzahl {@code …diamond.many}. */
    public String nameKey() {
        return "bw.resource." + id();
    }

    public static Optional<Resource> byId(String id) {
        try {
            return Optional.of(valueOf(id.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Ressource zu einem Material-Namen. */
    public static Optional<Resource> byMaterial(String material) {
        for (Resource resource : values()) {
            if (resource.material.equalsIgnoreCase(material)) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }
}
