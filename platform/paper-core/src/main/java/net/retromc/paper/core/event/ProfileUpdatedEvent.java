package net.retromc.paper.core.event;

import java.util.Objects;
import java.util.Set;
import net.retromc.paper.core.profile.ProfileView;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Profil eines verbundenen Spielers neu geladen ({@code PROFILE_CHANGED}, {@code PROFILE_INVALIDATE}, Social, Rang);
 * Haupt-Thread. Scoreboard, Tab und Nametag zeichnen danach neu (Spec 02 §6.3).
 */
public final class ProfileUpdatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ProfileView profile;
    private final Set<String> fields;

    public ProfileUpdatedEvent(Player player, ProfileView profile, Set<String> fields) {
        this.player = Objects.requireNonNull(player, "player");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.fields = Set.copyOf(fields);
    }

    /** Spieler. */
    public Player player() {
        return player;
    }

    /** Neues Profil. */
    public ProfileView profile() {
        return profile;
    }

    /** Geänderte Felder ({@code Profile}-Konstanten, {@code friends}); leer = alle. */
    public Set<String> fields() {
        return fields;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
