package net.retromc.paper.core.coins;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.economy.Booking;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.stats.CoinPayout;
import org.jspecify.annotations.Nullable;

/**
 * Coins und CoinSystem (Spec 02 §6.11, §8.1 {@code CoinService}): Buchungen über den {@code WalletService}
 * (write-through, Ledger, idempotent), Meldungen mit Präfix {@code §7[§eCoins§7] } und die Rundenauszahlung mit
 * Booster-Bonus {@code floor(base × min(0,1·n, 0,4))}. Coins sind nicht übertragbar.
 */
public interface CoinService {

    /** Kontostand eines hier verbundenen Spielers aus dem Profil; sonst 0 (dann {@link #fetchBalance}). */
    long balance(UUID player);

    CompletableFuture<Long> fetchBalance(UUID player);

    /** Einzelgutschrift außerhalb von Rundenenden; der Spieler erhält {@code coins.received}. */
    CompletableFuture<Booking> credit(UUID player, long amount, LedgerReason reason, @Nullable String idemKey);

    /** Abbuchung; {@code false} bei zu wenig Coins (nichts gebucht) oder Doppelbuchung. */
    CompletableFuture<Boolean> debit(UUID player, long amount, LedgerReason reason, @Nullable String idemKey);

    /**
     * Rundenauszahlung: Basisbetrag je Spieler vom Modul, Booster = Spieler der Runde mit Boost-Rang (Supremium,
     * Supremium+). Bucht {@code ROUND} und {@code ROUND_BOOST} idempotent je {@code matchId} und sendet
     * {@code coins.round} und {@code coins.boost}.
     *
     * @param base Basisbetrag je Spieler (0 = keine Zeile, z. B. GunGame, FFA)
     * @param roundPlayers alle Spieler der Runde beim Rundenende (bestimmen die Booster)
     */
    CompletableFuture<List<CoinPayout>> payoutRound(UUID matchId, Map<UUID, Long> base,
            Collection<UUID> roundPlayers);
}
