package net.retromc.core.data.games;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.economy.Booking;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.games.KitStore;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/**
 * Standard-{@link KitStore} auf {@code games.kit_owned} und {@code games.kit_selected}. Kauf: Besitz prüfen, Coins
 * über {@link WalletService#book} mit {@link KitStore#idemKey} abbuchen (Journal sperrt die Wallet-Zeile und lässt
 * denselben Schlüssel nur einmal zu), danach den Besitz idempotent anlegen. Wird die Buchung als Duplikat erkannt
 * (Doppelklick, Wiederholung nach Abbruch), wird nur noch der Besitz nachgezogen – Journal und Besitz bleiben
 * konsistent.
 */
public final class KitRepository implements KitStore {

    private final AsyncDatabase database;
    private final WalletService wallets;

    public KitRepository(AsyncDatabase database, WalletService wallets) {
        this.database = Objects.requireNonNull(database, "database");
        this.wallets = Objects.requireNonNull(wallets, "wallets");
    }

    @Override
    public CompletableFuture<Set<String>> owned(UUID player, String mode) {
        return database.query(connection -> Set.copyOf(new HashSet<>(Jdbc.list(connection,
                "SELECT kit_id FROM games.kit_owned WHERE uuid = ? AND mode = ?", row -> row.getString("kit_id"),
                player, mode))));
    }

    @Override
    public CompletableFuture<Optional<String>> selected(UUID player, String mode) {
        return database.query(connection -> Jdbc.one(connection,
                "SELECT kit_id FROM games.kit_selected WHERE uuid = ? AND mode = ?", row -> row.getString("kit_id"),
                player, mode));
    }

    @Override
    public CompletableFuture<Void> select(UUID player, String mode, String kitId) {
        check(mode, kitId);
        return database.transaction(connection -> {
            Jdbc.update(connection, """
                    INSERT INTO games.kit_selected (uuid, mode, kit_id) VALUES (?, ?, ?)
                    ON CONFLICT (uuid, mode) DO UPDATE SET kit_id = EXCLUDED.kit_id""", player, mode, kitId);
            return null;
        });
    }

    @Override
    public CompletableFuture<Purchase> buy(UUID player, String mode, String kitId, long price) {
        check(mode, kitId);
        if (price < 0) {
            throw new IllegalArgumentException("Preis ≥ 0: " + price);
        }
        return owned(player, mode).thenCompose(owned -> {
            if (owned.contains(kitId)) {
                return wallets.wallet(player).thenApply(wallet -> new Purchase(Status.OWNED, wallet.coins(), 0));
            }
            if (price == 0) {
                return grant(player, mode, kitId, 0).thenCompose(ignored -> wallets.wallet(player))
                        .thenApply(wallet -> new Purchase(Status.BOUGHT, wallet.coins(), 0));
            }
            return wallets.book(player, Currency.COINS, -price, LedgerReason.KIT_PURCHASE,
                    KitStore.idemKey(player, mode, kitId)).thenCompose(booking -> {
                        if (booking.status() == Booking.Status.INSUFFICIENT) {
                            return CompletableFuture.completedFuture(new Purchase(Status.INSUFFICIENT,
                                    booking.oldBalance(), price - booking.oldBalance()));
                        }
                        return grant(player, mode, kitId, price).thenApply(ignored -> new Purchase(Status.BOUGHT,
                                booking.newBalance(), 0));
                    });
        });
    }

    private CompletableFuture<Void> grant(UUID player, String mode, String kitId, long price) {
        return database.transaction(connection -> {
            Jdbc.update(connection, """
                    INSERT INTO games.kit_owned (uuid, mode, kit_id, price) VALUES (?, ?, ?, ?)
                    ON CONFLICT (uuid, mode, kit_id) DO NOTHING""", player, mode, kitId, price);
            return null;
        });
    }

    private static void check(String mode, String kitId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(kitId, "kitId");
        if (mode.isEmpty() || mode.length() > 24 || kitId.isEmpty() || kitId.length() > 32) {
            throw new IllegalArgumentException("Modus 1–24, Kit 1–32 Zeichen: " + mode + "/" + kitId);
        }
    }
}
