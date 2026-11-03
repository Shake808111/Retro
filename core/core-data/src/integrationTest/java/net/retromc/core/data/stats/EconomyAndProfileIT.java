package net.retromc.core.data.stats;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.economy.Booking;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.economy.Wallet;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.stats.AnchorType;
import net.retromc.core.api.stats.DisplayAnchor;
import net.retromc.core.api.stats.LevelUp;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.util.LevelCurve;
import net.retromc.core.data.economy.WalletRepository;
import net.retromc.core.data.social.DataFixture;
import net.retromc.core.data.social.PlayerRepository;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Konten (Spec 02 §6.11), Anzeige-Anker (§3 {@code /statswall}), globales Level (§8.1) und Profilfelder (§2.2). */
class EconomyAndProfileIT {

    DataFixture fixture;
    WalletRepository wallets;

    @BeforeEach
    void setUp() {
        fixture = new DataFixture();
        wallets = new WalletRepository(fixture.async, fixture.valkey, fixture.events, fixture.clock);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void buchungenIdempotentUndNieNegativ() {
        UUID player = fixture.player("Rich");
        assertEquals(Wallet.empty(player), await(wallets.wallet(player)));
        Booking credit = await(wallets.book(player, Currency.COINS, 475, LedgerReason.ROUND, "round:m1:" + player));
        assertEquals(new Booking(player, Currency.COINS, Booking.Status.APPLIED, 0, 475, credit.ledgerId()), credit);
        Booking again = await(wallets.book(player, Currency.COINS, 475, LedgerReason.ROUND, "round:m1:" + player));
        assertEquals(Booking.Status.DUPLICATE, again.status());
        assertEquals(475, again.newBalance());
        Booking tooMuch = await(wallets.book(player, Currency.COINS, -500, LedgerReason.ADMIN, null));
        assertEquals(Booking.Status.INSUFFICIENT, tooMuch.status());
        assertEquals(Booking.Status.APPLIED, await(wallets.book(player, Currency.COINS, -75, LedgerReason.PURCHASE,
                null)).status());
        Booking set = await(wallets.set(player, Currency.GOLD, 4_950, LedgerReason.ADMIN, null));
        assertEquals(4_950, set.newBalance());
        assertEquals(Booking.Status.APPLIED, await(wallets.book(player, Currency.RESET_TOKEN, 2, LedgerReason.PURCHASE,
                "tx:1")).status());
        assertEquals(new Wallet(player, 400, 4_950, 2), await(wallets.wallet(player)));
        assertEquals(4, TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM economy.ledger WHERE uuid = ?", player));
        assertEquals(1, TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM economy.ledger WHERE uuid = ? AND currency = 'GOLD' AND delta = 4950", player));
        // Coins und Gold melden PROFILE_CHANGED, Tokens nicht
        assertEquals(List.of(new ProfileChanged(player, List.of(ProfileChanged.COINS)),
                new ProfileChanged(player, List.of(ProfileChanged.COINS)),
                new ProfileChanged(player, List.of(ProfileChanged.GOLD))), fixture.events.of(ProfileChanged.class));
    }

    @Test
    void profilEnthaeltSpec02Felder() {
        UUID player = fixture.player("Profiled");
        PlayerRepository profiles = new PlayerRepository(fixture.async, fixture.valkey, () -> fixture.ranks,
                fixture.events);
        await(wallets.book(player, Currency.COINS, 1_355_470, LedgerReason.ADMIN, null));
        await(wallets.book(player, Currency.GOLD, 4_950, LedgerReason.ADMIN, null));
        NetworkLevelRepository levels = new NetworkLevelRepository(fixture.async, fixture.clock);
        await(levels.addXp(player, "test", LevelCurve.xpForLevel(3)));
        StatsRepository stats = new StatsRepository(fixture.async, fixture.valkey, () -> fixture.ranks,
                fixture.events, fixture.clock);
        await(stats.setHidden(player, true));
        Profile profile = await(profiles.profile(player)).orElseThrow();
        assertEquals(1_355_470, profile.coins());
        assertEquals(4_950, profile.gold());
        assertEquals(3, profile.level());
        assertTrue(profile.statsHidden());
        Profile fresh = await(profiles.profile(fixture.player("Fresh"))).orElseThrow();
        assertEquals(0, fresh.coins());
        assertEquals(0, fresh.level());
        assertFalse(fresh.statsHidden());
    }

    @Test
    void levelAufstiegNachKurve() {
        UUID player = fixture.player("Leveler");
        NetworkLevelRepository levels = new NetworkLevelRepository(fixture.async, fixture.clock);
        assertEquals(0, await(levels.level(player)).level());
        assertEquals(Optional.empty(), await(levels.addXp(player, "kill", 499)));
        assertEquals(Optional.of(new LevelUp(player, 0, 1, 500)), await(levels.addXp(player, "kill", 1)));
        assertEquals(Optional.of(new LevelUp(player, 1, 3, 3_000)), await(levels.addXp(player, "bingo", 2_500)));
        assertEquals(3, await(levels.level(player)).level());
        assertEquals(3, TestTransactions.count(fixture.database.dataSource(),
                "SELECT level FROM stats.network_level WHERE uuid = ?", player));
        assertThrows(IllegalArgumentException.class, () -> levels.addXp(player, "x", -1));
    }

    @Test
    void ankerAnlegenAuflistenEntfernen() {
        DisplayAnchorRepository anchors = new DisplayAnchorRepository(fixture.async);
        UUID creator = UUID.randomUUID();
        DisplayAnchor wall = await(anchors.create(new DisplayAnchor(0, "lobby-bedwars", "world", AnchorType.SIGN_WALL,
                ModeKey.BEDWARS, StatPeriod.DAYS_30, 10, 1.5, 64, -3.5, 90f, "NORTH", creator)));
        DisplayAnchor holo = await(anchors.create(new DisplayAnchor(0, "lobby-bedwars", "world", AnchorType.HOLO_TOP,
                ModeKey.GUNGAME, StatPeriod.ALL, 5, 0, 70, 0, 0f, null, null)));
        await(anchors.create(new DisplayAnchor(0, "lobby", "world", AnchorType.HOLO_PERSONAL, ModeKey.SKYWARS,
                StatPeriod.ALL, 10, 0, 0, 0, 0f, null, null)));
        assertTrue(wall.id() > 0);
        assertEquals(List.of(wall, holo), await(anchors.list("lobby-bedwars")));
        assertTrue(await(anchors.delete(wall.id())));
        assertFalse(await(anchors.delete(wall.id())));
        assertEquals(List.of(holo), await(anchors.list("lobby-bedwars")));
        assertThrows(IllegalArgumentException.class, () -> await(anchors.create(new DisplayAnchor(0, "lobby", "world",
                AnchorType.HOLO_TOP, ModeKey.of("nope"), StatPeriod.ALL, 10, 0, 0, 0, 0f, null, null))));
    }
}
