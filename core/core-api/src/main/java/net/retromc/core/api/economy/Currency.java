package net.retromc.core.api.economy;

/** Währung einer Buchung ({@code economy.ledger.currency}, Spec 02 §2.1). */
public enum Currency {
    /** Netzwerkweite Coins ({@code economy.wallet.coins}). */
    COINS("coins"),
    /** Gold, zweite Währung (Scoreboard, CityBuild-Gold-Shop; Spec 02 O12). */
    GOLD("gold"),
    /** Token für {@code /resetstats}. */
    RESET_TOKEN("reset_tokens");

    private final String column;

    Currency(String column) {
        this.column = column;
    }

    /** Spalte in {@code economy.wallet}. */
    public String column() {
        return column;
    }
}
