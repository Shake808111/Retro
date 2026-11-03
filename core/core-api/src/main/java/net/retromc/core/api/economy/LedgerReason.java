package net.retromc.core.api.economy;

/** Grund einer Buchung ({@code economy.ledger.reason}, Spec 02 §6.11). */
public enum LedgerReason {
    ROUND,
    ROUND_BOOST,
    DAILY,
    LOTTERY,
    VOTE,
    ADMIN,
    PURCHASE,
    REFUND,
    STATS_RESET,
    CB_EXCHANGE,
    CB_GOLDSHOP,
    KIT_PURCHASE
}
