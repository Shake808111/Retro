package net.retromc.core.data.economy;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.economy.Booking;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.economy.Wallet;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.social.Profile;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link WalletService} auf {@code economy.wallet} und {@code economy.ledger} (Spec 02 §2.1, §6.11):
 * Zeile sperren, Stand prüfen, Journalzeile anhängen (Idempotenz über den eindeutigen {@code idem_key}) und Stand
 * schreiben – alles in einer Transaktion. Danach werden {@code profile:{uuid}.coins/gold} nachgezogen (falls im
 * Cache) und {@code PROFILE_CHANGED} gesendet.
 */
public final class WalletRepository implements WalletService {

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final EventSink events;
    private final Clock clock;

    public WalletRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey, EventSink events,
            Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<Wallet> wallet(UUID player) {
        return database.query(connection -> Jdbc.one(connection, "SELECT coins, gold, reset_tokens"
                + " FROM economy.wallet WHERE uuid = ?", row -> new Wallet(player, row.getLong("coins"),
                row.getLong("gold"), row.getLong("reset_tokens")), player).orElse(Wallet.empty(player)));
    }

    @Override
    public CompletableFuture<Booking> book(UUID player, Currency currency, long delta, LedgerReason reason,
            @Nullable String idemKey) {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(reason, "reason");
        return database.transaction(connection -> {
            long old = lockBalance(connection, player, currency);
            return apply(connection, player, currency, old, delta, reason, idemKey);
        }).thenCompose(this::afterBooking);
    }

    @Override
    public CompletableFuture<Booking> set(UUID player, Currency currency, long value, LedgerReason reason,
            @Nullable String idemKey) {
        if (value < 0) {
            throw new IllegalArgumentException("Stand darf nicht negativ sein: " + value);
        }
        return database.transaction(connection -> {
            long old = lockBalance(connection, player, currency);
            return apply(connection, player, currency, old, value - old, reason, idemKey);
        }).thenCompose(this::afterBooking);
    }

    /** Legt die Zeile bei Bedarf an und sperrt sie bis zum Ende der Transaktion. */
    private static long lockBalance(Connection connection, UUID player, Currency currency) throws SQLException {
        Jdbc.update(connection, "INSERT INTO economy.wallet (uuid) VALUES (?) ON CONFLICT DO NOTHING", player);
        return Jdbc.one(connection, "SELECT " + currency.column() + " FROM economy.wallet WHERE uuid = ? FOR UPDATE",
                row -> row.getLong(1), player).orElse(0L);
    }

    private Booking apply(Connection connection, UUID player, Currency currency, long old, long delta,
            LedgerReason reason, @Nullable String idemKey) throws SQLException {
        if (old + delta < 0) {
            return new Booking(player, currency, Booking.Status.INSUFFICIENT, old, old, 0);
        }
        Optional<Long> ledgerId = Jdbc.one(connection, "INSERT INTO economy.ledger (uuid, delta, reason, idem_key,"
                + " currency, created_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (idem_key) DO NOTHING RETURNING id",
                row -> row.getLong("id"), player, delta, reason.name(), idemKey, currency.name(), clock.instant());
        if (ledgerId.isEmpty()) {
            return new Booking(player, currency, Booking.Status.DUPLICATE, old, old, 0);
        }
        Jdbc.update(connection, "UPDATE economy.wallet SET " + currency.column() + " = ? WHERE uuid = ?", old + delta,
                player);
        return new Booking(player, currency, Booking.Status.APPLIED, old, old + delta, ledgerId.get());
    }

    /** Profil-Cache nachziehen und {@code PROFILE_CHANGED} senden (nur Coins und Gold sind Profilfelder). */
    private CompletableFuture<Booking> afterBooking(Booking booking) {
        if (!booking.applied() || booking.currency() == Currency.RESET_TOKEN) {
            return CompletableFuture.completedFuture(booking);
        }
        String field = booking.currency() == Currency.COINS ? Profile.COINS : Profile.GOLD;
        String key = ValkeyKeys.profile(booking.uuid());
        return Jdbc.future(valkey.exists(key))
                .thenCompose(cached -> cached != null && cached > 0
                        ? Jdbc.future(valkey.hset(key, field, Long.toString(booking.newBalance())))
                        : CompletableFuture.completedFuture(false))
                .handle((ignored, error) -> {
                    events.publish(new ProfileChanged(booking.uuid(), List.of(booking.currency() == Currency.COINS
                            ? ProfileChanged.COINS : ProfileChanged.GOLD)));
                    return booking;
                });
    }
}
