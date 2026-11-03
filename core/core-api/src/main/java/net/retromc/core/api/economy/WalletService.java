package net.retromc.core.api.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Konten und Buchungsjournal (Spec 02 §2.1, §6.11): write-through in {@code economy.wallet}, jede Änderung als Zeile
 * in {@code economy.ledger} (nur Anhängen), idempotent über {@code idem_key}. Nach jeder Buchung von Coins oder Gold
 * folgt {@code PROFILE_CHANGED} ({@code net:profile}). Standard ist {@code WalletRepository} (core-data).
 */
public interface WalletService {

    /** Kontostand; ohne Zeile alles 0. */
    CompletableFuture<Wallet> wallet(UUID player);

    /**
     * Bucht {@code delta} (positiv = Gutschrift, negativ = Abbuchung). Eine Abbuchung unter 0 wird nicht ausgeführt
     * ({@link Booking.Status#INSUFFICIENT}).
     *
     * @param idemKey Idempotenz-Schlüssel oder {@code null} (dann ohne Schutz vor Doppelbuchung)
     */
    CompletableFuture<Booking> book(UUID player, Currency currency, long delta, LedgerReason reason,
            @Nullable String idemKey);

    /** Setzt den Stand auf {@code value} (Differenzbuchung, z. B. {@code /coins set}). */
    CompletableFuture<Booking> set(UUID player, Currency currency, long value, LedgerReason reason,
            @Nullable String idemKey);
}
