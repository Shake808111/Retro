package net.retromc.core.api.stats;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Coin-Auszahlung eines Spielers am Rundenende (Spec 02 §6.11): Basisbetrag vom Modul plus Boost
 * {@code floor(base × min(0,1·n, 0,4))}; die Berechnung liegt in core-stats, die Buchung im {@code CoinService}.
 *
 * @param base Basisbetrag (Zeile {@code coins.round})
 * @param boost Bonus durch Booster (Zeile {@code coins.boost}; 0 = keine Boost-Zeile)
 * @param boosterCount Booster der Runde inklusive Empfänger (n der Formel, Spec 02 O2)
 * @param boosters andere Booster (ohne Empfänger) für die Namensliste
 */
public record CoinPayout(UUID player, long base, long boost, int boosterCount, List<UUID> boosters) {

    public CoinPayout {
        Objects.requireNonNull(player, "player");
        boosters = List.copyOf(boosters);
        if (base < 0 || boost < 0 || boosterCount < 0) {
            throw new IllegalArgumentException("Negative Werte in der Auszahlung");
        }
        if (boosters.contains(player)) {
            throw new IllegalArgumentException("Die Namensliste enthält den Empfänger nicht (Spec 02 O2)");
        }
    }

    public long total() {
        return base + boost;
    }
}
