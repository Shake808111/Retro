package net.retromc.paper.core.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * NPCs für Lobby, BedWars-Händler und CityBuild (Spec 02 §8.1 {@code NpcService}, angefordert von Spec 03 §8.3):
 * Spieler-Figuren mit Skin, Name, Blick zum nächsten Spieler und Klick-Handlern, ohne Fremd-Plugins. Nicht dauerhaft
 * gespeichert; nur auf dem Haupt-Thread.
 */
public interface NpcService {

    Npc spawn(NpcSpec spec);

    /**
     * Bauplan eines NPC.
     *
     * @param skin Profil mit {@code textures} oder {@code null} (Standard-Skin)
     * @param name Name über dem Kopf oder {@code null} ohne Namen
     * @param lookAtPlayers dreht den Kopf zum nächsten Spieler (8 Blöcke)
     */
    record NpcSpec(Location location, @Nullable PlayerProfile skin, @Nullable Component name, boolean lookAtPlayers,
            @Nullable Consumer<Player> onRightClick, @Nullable Consumer<Player> onLeftClick) {

        public NpcSpec {
            Objects.requireNonNull(location, "location");
        }
    }

    /** Ein erzeugter NPC. */
    interface Npc {

        Entity entity();

        void teleport(Location location);

        void remove();
    }
}
