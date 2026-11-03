package net.retromc.paper.core.coins;

import static org.incendo.cloud.parser.standard.LongParser.longParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.economy.Booking;
import net.retromc.core.api.economy.Currency;
import net.retromc.core.api.economy.LedgerReason;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.stats.CoinPayout;
import net.retromc.core.api.util.Registration;
import net.retromc.core.stats.CoinBoost;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.event.CoinsChangedEvent;
import net.retromc.paper.core.lang.Texts;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;

/**
 * Coins (Spec 02 §3, §6.11): {@link CoinService}, {@code /coins} (Stand), {@code /coins <add|remove|set> <Spieler>
 * <Betrag>} (Recht {@code <brand>.coins.admin}, Grund {@code ADMIN}) und {@code /tokens} (ResetTokens).
 */
public final class CoinFeature implements CoreFeature<PaperCore>, CoinService {

    /** Recht für {@code /coins add|remove|set}. */
    public static final String ADMIN_PERMISSION = BuildInfo.BRAND_ID + ".coins.admin";

    private @Nullable PaperCore core;
    private @Nullable Texts texts;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Coins";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        Texts helper = new Texts(context.services().supplier(I18n.class));
        texts = helper;
        PaperCommandManager<Source> manager = context.services().get(CommandService.class).manager();
        manager.command(manager.commandBuilder("coins").senderType(PlayerSource.class)
                .handler(ctx -> showBalance(ctx.sender().source())));
        for (String action : List.of("add", "remove", "set")) {
            manager.command(manager.commandBuilder("coins").literal(action).permission(ADMIN_PERMISSION)
                    .required("player", stringParser()).required("amount", longParser(0))
                    .handler(ctx -> admin(ctx.sender().source(), action, ctx.get("player"), ctx.get("amount"))));
        }
        manager.command(manager.commandBuilder("tokens").senderType(PlayerSource.class)
                .handler(ctx -> showTokens(ctx.sender().source())));
        registration = context.services().registerDefault(CoinService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        texts = null;
        core = null;
    }

    private PaperCore context() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Coins nicht aktiv");
        }
        return context;
    }

    private WalletService wallets() {
        return context().services().get(WalletService.class);
    }

    private Component prefix(Locale locale) {
        return context().services().get(I18n.class).render(locale, "prefix.coins");
    }

    // ------------------------------------------------------------------------------------------ CoinService

    @Override
    public long balance(UUID player) {
        return context().services().find(PlayerProfiles.class).flatMap(profiles -> profiles.view(player))
                .map(view -> view.profile().coins()).orElse(0L);
    }

    @Override
    public CompletableFuture<Long> fetchBalance(UUID player) {
        return wallets().wallet(player).thenApply(wallet -> wallet.coins());
    }

    @Override
    public CompletableFuture<Booking> credit(UUID player, long amount, LedgerReason reason, @Nullable String idemKey) {
        if (amount < 0) {
            throw new IllegalArgumentException("Gutschrift darf nicht negativ sein: " + amount);
        }
        CompletableFuture<Booking> booking = wallets().book(player, Currency.COINS, amount, reason, idemKey);
        context().mainThread().accept(booking, "Coin-Gutschrift", done -> {
            changed(done, reason);
            Player online = Bukkit.getPlayer(player);
            if (done.applied() && online != null) {
                Texts helper = helper();
                online.sendMessage(helper.render(online, "coins.received", Placeholder.component("prefix",
                        prefix(helper.locale(online))), helper.number(online, "n", amount)));
            }
        });
        return booking;
    }

    @Override
    public CompletableFuture<Boolean> debit(UUID player, long amount, LedgerReason reason, @Nullable String idemKey) {
        if (amount < 0) {
            throw new IllegalArgumentException("Abbuchung darf nicht negativ sein: " + amount);
        }
        CompletableFuture<Booking> booking = wallets().book(player, Currency.COINS, -amount, reason, idemKey);
        context().mainThread().accept(booking, "Coin-Abbuchung", done -> changed(done, reason));
        return booking.thenApply(Booking::applied);
    }

    @Override
    public CompletableFuture<List<CoinPayout>> payoutRound(UUID matchId, Map<UUID, Long> base,
            Collection<UUID> roundPlayers) {
        List<UUID> players = roundPlayers.stream().distinct().toList();
        PaperCore context = context();
        return context.services().get(RankService.class).ranks(players).thenCompose(ranks -> {
            List<UUID> boosters = players.stream().filter(uuid -> {
                RankInfo rank = ranks.get(uuid);
                return rank != null && rank.flags().booster();
            }).toList();
            List<CoinPayout> payouts = new ArrayList<>();
            List<CompletableFuture<?>> bookings = new ArrayList<>();
            base.forEach((player, amount) -> {
                CoinPayout payout = CoinBoost.payout(player, amount, boosters);
                payouts.add(payout);
                if (payout.base() > 0) {
                    bookings.add(wallets().book(player, Currency.COINS, payout.base(), LedgerReason.ROUND,
                            "round:" + matchId + ":" + player));
                }
                if (payout.boost() > 0) {
                    bookings.add(wallets().book(player, Currency.COINS, payout.boost(), LedgerReason.ROUND_BOOST,
                            "boost:" + matchId + ":" + player));
                }
            });
            Map<UUID, RankInfo> rankMap = new LinkedHashMap<>(ranks);
            return CompletableFuture.allOf(bookings.toArray(CompletableFuture[]::new))
                    .thenCompose(ignored -> context.services().get(ProfileService.class).profiles(boosters))
                    .thenApply(profiles -> {
                        context.mainThread().execute(() -> payouts.forEach(payout -> announce(payout, rankMap,
                                profiles)));
                        return List.copyOf(payouts);
                    });
        });
    }

    /** {@code coins.round} und {@code coins.boost} an einen verbundenen Spieler. */
    private void announce(CoinPayout payout, Map<UUID, RankInfo> ranks, Map<UUID, Profile> profiles) {
        Player player = Bukkit.getPlayer(payout.player());
        if (player == null) {
            return;
        }
        Texts helper = helper();
        Locale locale = helper.locale(player);
        Component prefix = prefix(locale);
        if (payout.base() > 0) {
            player.sendMessage(helper.render(player, "coins.round", Placeholder.component("prefix", prefix),
                    helper.number(player, "n", payout.base())));
        }
        if (payout.boost() <= 0) {
            return;
        }
        List<UUID> named = payout.boosters().isEmpty() ? List.of(payout.player()) : payout.boosters();
        String rank = named.stream().map(ranks::get).filter(Objects::nonNull)
                .max(Comparator.comparingInt(RankInfo::weight)).map(RankInfo::displayName).orElse("")
                .toUpperCase(Locale.ROOT);
        List<String> names = named.stream().map(uuid -> Optional.ofNullable(profiles.get(uuid))
                .map(Profile::visibleName).orElseGet(() -> nameOf(uuid))).toList();
        CoinBoost.Names list = CoinBoost.Names.of(names);
        Component nameList = helper.render(player, list.key(), Placeholder.component("first",
                Component.text(list.first(), NamedTextColor.AQUA)), Placeholder.component("second",
                Component.text(list.second(), NamedTextColor.AQUA)), Placeholder.unparsed("n",
                Integer.toString(list.more())));
        player.sendMessage(helper.render(player, "coins.boost", Placeholder.component("prefix", prefix),
                helper.number(player, "n", payout.boost()), Placeholder.unparsed("rank", rank),
                Placeholder.component("names", nameList)));
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString().substring(0, 8);
    }

    private void changed(Booking booking, LedgerReason reason) {
        if (booking.applied()) {
            Bukkit.getPluginManager().callEvent(new CoinsChangedEvent(booking.uuid(), booking.currency(),
                    booking.oldBalance(), booking.newBalance(), reason));
        }
    }

    private Texts helper() {
        Texts current = texts;
        if (current == null) {
            throw new IllegalStateException("Coins nicht aktiv");
        }
        return current;
    }

    // ------------------------------------------------------------------------------------------ Befehle

    private void showBalance(Player player) {
        PaperCore context = context();
        context.mainThread().accept(wallets().wallet(player.getUniqueId()), "/coins", wallet -> player.sendMessage(
                helper().render(player, "coins.balance", Placeholder.component("prefix",
                        prefix(helper().locale(player))), helper().number(player, "n", wallet.coins()))));
    }

    private void showTokens(Player player) {
        context().mainThread().accept(wallets().wallet(player.getUniqueId()), "/tokens", wallet -> helper()
                .send(player, "tokens.show", helper().number(player, "n", wallet.resetTokens())));
    }

    private void admin(CommandSender sender, String action, String name, long amount) {
        PaperCore context = context();
        CompletableFuture<Optional<UUID>> target = context.services().get(ProfileService.class).uuidOf(name);
        context.mainThread().accept(target, "/coins " + action, found -> {
            if (found.isEmpty()) {
                helper().send(sender, "stats.notfound");
                return;
            }
            UUID uuid = found.get();
            CompletableFuture<Booking> booking = switch (action) {
                case "add" -> wallets().book(uuid, Currency.COINS, amount, LedgerReason.ADMIN, null);
                case "remove" -> wallets().book(uuid, Currency.COINS, -amount, LedgerReason.ADMIN, null);
                default -> wallets().set(uuid, Currency.COINS, amount, LedgerReason.ADMIN, null);
            };
            context.mainThread().accept(booking.exceptionally(error -> null), "/coins " + action, done -> {
                Component prefix = prefix(helper().locale(sender));
                if (done == null) {
                    helper().send(sender, "error.data");
                } else if (done.status() == Booking.Status.INSUFFICIENT) {
                    helper().send(sender, "coins.insufficient", Placeholder.component("prefix", prefix));
                } else {
                    changed(done, LedgerReason.ADMIN);
                    helper().send(sender, "coins.admin-done", Placeholder.component("prefix", prefix),
                            Placeholder.unparsed("name", name), helper().number(sender, "n", done.newBalance()));
                }
            });
        });
    }
}
