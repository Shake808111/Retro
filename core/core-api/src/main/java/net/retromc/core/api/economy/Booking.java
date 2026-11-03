package net.retromc.core.api.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * Ergebnis einer Buchung (Spec 02 §6.11, {@code CoinsChangedEvent}).
 *
 * @param status Ausgang
 * @param oldBalance Stand vorher
 * @param newBalance Stand nachher (bei {@link Status#INSUFFICIENT} und {@link Status#DUPLICATE} = aktueller Stand)
 * @param ledgerId Zeile in {@code economy.ledger} oder 0
 */
public record Booking(UUID uuid, Currency currency, Status status, long oldBalance, long newBalance, long ledgerId) {

    public Booking {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    /** Ausgang einer Buchung. */
    public enum Status {
        /** Gebucht. */
        APPLIED,
        /** {@code idem_key} schon verbucht, nichts geändert. */
        DUPLICATE,
        /** Abbuchung größer als der Stand ({@code coins.insufficient}). */
        INSUFFICIENT
    }
}
