package net.retromc.core.data.games;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.games.KitStore;
import net.retromc.core.api.games.SkyWarsStore.KitSettings;
import net.retromc.core.data.economy.WalletRepository;
import net.retromc.core.data.social.DataFixture;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #15: Kit-Kauf ist idempotent (Doppelklick bucht einmal), Coins-Journal und {@code games.kit_owned}
 * bleiben konsistent; dazu Kit-Auswahl und Kit-Einstellungen gegen PostgreSQL 18.6 ({@code V4_1}, {@code V4_2}).
 */
class SkyWarsRepositoryIT {

    DataFixture fixture;
    WalletRepository wallets;
    KitRepository kits;
    SkyWarsRepository skywars;

    @BeforeEach
    void setUp() {
        fixture = new DataFixture();
        wallets = new WalletRepository(fixture.async, fixture.valkey, fixture.events, fixture.clock);
        kits = new KitRepository(fixture.async, wallets);
        skywars = new SkyWarsRepository(fixture.async);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private long ledger(UUID player) {
        return TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM economy.ledger WHERE uuid = ? AND reason = 'KIT_PURCHASE'", player);
    }

    private long owned(UUID player, String kit) {
        return TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM games.kit_owned WHERE uuid = ? AND mode = 'skywars' AND kit_id = ?",
                player, kit);
    }

    @Test
    void doppelklickBuchtGenauEinmal() {
        UUID player = fixture.player("Engel");
        await(wallets.book(player, Currency.COINS, 30_000, LedgerReason.ADMIN, null));
        // zwei gleichzeitige Käufe (Doppelklick) und ein späterer dritter
        CompletableFuture<KitStore.Purchase> first = kits.buy(player, "skywars", "engel", 25_000);
        CompletableFuture<KitStore.Purchase> second = kits.buy(player, "skywars", "engel", 25_000);
        List<KitStore.Status> results = List.of(await(first).status(), await(second).status());
        assertTrue(results.stream().allMatch(status -> status == KitStore.Status.BOUGHT
                || status == KitStore.Status.OWNED), results.toString());
        assertEquals(KitStore.Status.OWNED, await(kits.buy(player, "skywars", "engel", 25_000)).status());
        assertEquals(5_000, await(wallets.wallet(player)).coins(), "genau eine Abbuchung");
        assertEquals(1, ledger(player));
        assertEquals(1, owned(player, "engel"));
        assertEquals(Set.of("engel"), await(kits.owned(player, "skywars")));
        assertEquals(Set.of(), await(kits.owned(player, "endergames")), "Modi getrennt");
    }

    @Test
    void zuWenigCoinsBuchtNichts() {
        UUID player = fixture.player("Arm");
        await(wallets.book(player, Currency.COINS, 10_000, LedgerReason.ADMIN, null));
        KitStore.Purchase purchase = await(kits.buy(player, "skywars", "teufel", 60_000));
        assertEquals(KitStore.Status.INSUFFICIENT, purchase.status());
        assertEquals(50_000, purchase.missing());
        assertEquals(10_000, await(wallets.wallet(player)).coins());
        assertEquals(0, ledger(player));
        assertEquals(0, owned(player, "teufel"));
    }

    @Test
    void abgebrochenerKaufWirdNachgezogen() {
        UUID player = fixture.player("Abbruch");
        await(wallets.book(player, Currency.COINS, 40_000, LedgerReason.ADMIN, null));
        // Buchung lief durch, der Besitz fehlt (z. B. Absturz zwischen beiden Schritten)
        await(wallets.book(player, Currency.COINS, -40_000, LedgerReason.KIT_PURCHASE,
                KitStore.idemKey(player, "skywars", "jaeger")));
        KitStore.Purchase retry = await(kits.buy(player, "skywars", "jaeger", 40_000));
        assertEquals(KitStore.Status.BOUGHT, retry.status());
        assertEquals(0, await(wallets.wallet(player)).coins(), "keine zweite Abbuchung");
        assertEquals(1, ledger(player));
        assertEquals(1, owned(player, "jaeger"));
    }

    @Test
    void kostenlosesKitUndAuswahl() {
        UUID player = fixture.player("Starter");
        assertEquals(KitStore.Status.BOUGHT, await(kits.buy(player, "skywars", "starter", 0)).status());
        assertEquals(0, ledger(player));
        assertEquals(Optional.empty(), await(kits.selected(player, "skywars")));
        await(kits.select(player, "skywars", "starter"));
        await(kits.select(player, "skywars", KitStore.RANDOM));
        assertEquals(Optional.of(KitStore.RANDOM), await(kits.selected(player, "skywars")));
    }

    @Test
    void kitEinstellungen() {
        UUID player = fixture.player("Sammler");
        assertEquals(KitSettings.DEFAULT, await(skywars.kitSettings(player)));
        KitSettings changed = KitSettings.DEFAULT.withLegacy(true).toggleHidden("ziegel").toggleHidden("angler");
        await(skywars.saveKitSettings(player, changed));
        assertEquals(changed, await(skywars.kitSettings(player)));
        await(skywars.saveKitSettings(player, changed.toggleHidden("ziegel")));
        assertEquals(Set.of("angler"), await(skywars.kitSettings(player)).hiddenKits());
    }
}
