package net.retromc.paper.core.holo;

import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Hologramme aus {@code TextDisplay}-Entities (Spec 02 §4.9, §8.1 {@code HologramService}): persönlich (nur für einen
 * Betrachter, {@code visibleByDefault=false}) oder für alle. Nicht dauerhaft gespeichert; nur auf dem Haupt-Thread.
 */
public interface HologramService {

    /** Hologramm nur für {@code viewer}. */
    Hologram personal(Player viewer, Location location, List<Component> lines);

    /** Hologramm für alle; {@link Hologram#refresh()} liest die Zeilen neu. */
    Hologram global(Location location, Supplier<List<Component>> lines);

    /** Ein angezeigtes Hologramm. */
    interface Hologram {

        void update(List<Component> lines);

        /** Liest die Zeilen eines globalen Hologramms neu (persönlich: ohne Wirkung). */
        void refresh();

        void remove();

        Location location();
    }
}
