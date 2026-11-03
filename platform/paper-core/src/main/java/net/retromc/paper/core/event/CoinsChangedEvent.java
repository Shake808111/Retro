package net.retromc.paper.core.event;

import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Nach einer Buchung über den {@code CoinService} dieses Servers (Spec 02 §8.2); Haupt-Thread. */
public final class CoinsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Currency currency;
    private final long oldBalance;
    private final long newBalance;
    private final LedgerReason reason;

    public CoinsChangedEvent(UUID player, Currency currency, long oldBalance, long newBalance, LedgerReason reason) {
        this.player = Objects.requireNonNull(player, "player");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Spieler. */
    public UUID player() {
        return player;
    }

    /** Währung. */
    public Currency currency() {
        return currency;
    }

    /** Stand vorher. */
    public long oldBalance() {
        return oldBalance;
    }

    /** Stand nachher. */
    public long newBalance() {
        return newBalance;
    }

    /** Buchungsgrund. */
    public LedgerReason reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
