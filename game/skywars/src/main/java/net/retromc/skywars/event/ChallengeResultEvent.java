package net.retromc.skywars.event;

import java.util.Objects;
import net.retromc.skywars.challenge.Challenge;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Ergebnis der Runden-Challenge eines Spielers (Spec 04 §6.2.6, §8.2). */
public final class ChallengeResultEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Challenge challenge;
    private final boolean success;

    public ChallengeResultEvent(Player player, Challenge challenge, boolean success) {
        this.player = Objects.requireNonNull(player, "player");
        this.challenge = Objects.requireNonNull(challenge, "challenge");
        this.success = success;
    }

    public Player player() {
        return player;
    }

    public Challenge challenge() {
        return challenge;
    }

    public boolean success() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
