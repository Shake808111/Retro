package net.retromc.paper.core.event;

import java.util.Objects;
import net.retromc.paper.core.profile.ProfileView;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Profil beim Join geladen, vor dem ersten Board (Spec 02 §8.2); Haupt-Thread. */
public final class ProfileLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ProfileView profile;

    public ProfileLoadedEvent(Player player, ProfileView profile) {
        this.player = Objects.requireNonNull(player, "player");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /** Spieler. */
    public Player player() {
        return player;
    }

    /** Geladenes Profil. */
    public ProfileView profile() {
        return profile;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
