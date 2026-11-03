package net.retromc.paper.core.stats;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.ResetResult;
import net.retromc.core.api.stats.ResetScope;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.api.stats.SubmitResult;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.api.util.NetworkTime;
import net.retromc.core.api.util.Registration;
import net.retromc.core.stats.RollingWindow;
import net.retromc.core.stats.StatsReport;
import net.retromc.core.stats.StatsRules;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.event.StatsResetEvent;
import net.retromc.paper.core.event.StatsSubmittedEvent;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.lang.Texts;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.paper.core.profile.RankDisplayService;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;

/**
 * Statistik auf Paper (Spec 02 §3, §6.4–§6.10): {@link StatsService}, die Befehle {@code /stats}, {@code /statsall}
 * ({@code /statsalltime}), {@code /statsd}, {@code /togglestats}, {@code /top}, {@code /resetstats} ({@code /ja},
 * Chat „Ja“/„Nein“), {@code /statsadmin}, das Statistik-Profil (§5.4) und die Jobs (Rollover 00:00:05, Reconcile
 * 04:00, Partitionen; je Tag einmal im Netzwerk über Valkey-Sperren).
 */
public final class StatsFeature implements CoreFeature<PaperCore>, Listener, StatsService {

    /** Recht für {@code /statsadmin}. */
    public static final String ADMIN_PERMISSION = BuildInfo.BRAND_ID + ".stats.admin";
    private static final long JOB_CHECK_TICKS = 20;

    /** Zustand eines offenen Statistik-Profils. */
    private record ProfileState(UUID target, @Nullable Runnable back, Map<ModeKey, StatPeriod> periods,
            StatPeriod global) {
    }

    private final Map<UUID, Instant> dialogs = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> toggles = new ConcurrentHashMap<>();
    private final Map<UUID, ProfileState> profiles = new ConcurrentHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private volatile @Nullable ServerMode override;
    private @Nullable PaperCore core;
    private @Nullable Texts texts;
    private @Nullable BukkitTask jobs;
    private @Nullable LocalDate housekept;
    private @Nullable LocalDate rolledOver;
    private @Nullable LocalDate reconciled;

    @Override
    public String name() {
        return "Statistik";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        texts = new Texts(context.services().supplier(I18n.class));
        registerCommands(context.services().get(CommandService.class).manager());
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registrations.add(context.services().registerDefault(StatsService.class, this));
        jobs = Bukkit.getScheduler().runTaskTimerAsynchronously(context.plugin(), this::runJobs, JOB_CHECK_TICKS,
                JOB_CHECK_TICKS);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        BukkitTask task = jobs;
        if (task != null) {
            task.cancel();
        }
        jobs = null;
        registrations.forEach(Registration::close);
        registrations.clear();
        dialogs.clear();
        toggles.clear();
        profiles.clear();
        texts = null;
        core = null;
    }

    private PaperCore context() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Statistik nicht aktiv");
        }
        return context;
    }

    private Texts texts() {
        Texts current = texts;
        if (current == null) {
            throw new IllegalStateException("Statistik nicht aktiv");
        }
        return current;
    }

    private StatsStore store() {
        return context().services().get(StatsStore.class);
    }

    private ModeCatalog catalog() {
        return context().services().get(ModeCatalog.class);
    }

    // ------------------------------------------------------------------------------------------ Befehle

    private void registerCommands(PaperCommandManager<Source> manager) {
        manager.command(manager.commandBuilder("stats").optional("args", greedyStringParser())
                .handler(ctx -> stats(ctx.sender().source(), ctx.getOrDefault("args", ""), StatWindow.THIRTY_DAYS)));
        manager.command(manager.commandBuilder("statsall", "statsalltime").optional("args", greedyStringParser())
                .handler(ctx -> stats(ctx.sender().source(), ctx.getOrDefault("args", ""), StatWindow.ALL)));
        manager.command(manager.commandBuilder("statsd").optional("args", greedyStringParser())
                .handler(ctx -> statsDays(ctx.sender().source(), ctx.getOrDefault("args", ""))));
        manager.command(manager.commandBuilder("togglestats").senderType(PlayerSource.class)
                .handler(ctx -> toggleStats(ctx.sender().source())));
        manager.command(manager.commandBuilder("top").senderType(PlayerSource.class)
                .handler(ctx -> top(ctx.sender().source(), StatPeriod.DAYS_30)));
        manager.command(manager.commandBuilder("top").literal("30d").senderType(PlayerSource.class)
                .handler(ctx -> top(ctx.sender().source(), StatPeriod.DAYS_30)));
        manager.command(manager.commandBuilder("top").literal("statsall").senderType(PlayerSource.class)
                .handler(ctx -> top(ctx.sender().source(), StatPeriod.ALL)));
        manager.command(manager.commandBuilder("resetstats").senderType(PlayerSource.class)
                .handler(ctx -> openReset(ctx.sender().source())));
        manager.command(manager.commandBuilder("resetstats").literal("confirm").senderType(PlayerSource.class)
                .handler(ctx -> confirmReset(ctx.sender().source())));
        manager.command(manager.commandBuilder("resetstats").literal("cancel").senderType(PlayerSource.class)
                .handler(ctx -> cancelReset(ctx.sender().source())));
        manager.command(manager.commandBuilder("ja").senderType(PlayerSource.class)
                .handler(ctx -> confirmReset(ctx.sender().source())));
        manager.command(manager.commandBuilder("statsadmin").literal("reset").permission(ADMIN_PERMISSION)
                .required("player", stringParser()).required("mode", stringParser())
                .handler(ctx -> adminReset(ctx.sender().source(), ctx.get("player"), ctx.get("mode"))));
        manager.command(manager.commandBuilder("statsadmin").literal("rebuild").permission(ADMIN_PERMISSION)
                .required("mode", stringParser())
                .handler(ctx -> adminRebuild(ctx.sender().source(), ctx.get("mode"))));
    }

    private void stats(CommandSender sender, String args, StatWindow window) {
        StatsArgs.Parsed parsed = StatsArgs.stats(args, window, serverMode().mode(), catalog()::find);
        handle(sender, parsed);
    }

    private void statsDays(CommandSender sender, String args) {
        handle(sender, StatsArgs.days(args, serverMode().mode(), catalog()::find));
    }

    private void handle(CommandSender sender, StatsArgs.Parsed parsed) {
        StatsArgs.Request request = parsed.request();
        if (request == null) {
            String error = parsed.error() == null ? "error.data" : parsed.error();
            texts().send(sender, error, Placeholder.unparsed("mode", parsed.argument()));
            return;
        }
        sendStats(sender, request.mode(), request.window(), request.target());
    }

    // ------------------------------------------------------------------------------------------ StatsService

    @Override
    public CompletableFuture<SubmitResult> submit(MatchResult result) {
        CompletableFuture<SubmitResult> submitted = store().submit(result);
        context().mainThread().accept(submitted, "Rundenende " + result.matchId(), done -> {
            if (done.applied()) {
                Bukkit.getPluginManager().callEvent(new StatsSubmittedEvent(result, done.deltas()));
            }
        });
        return submitted;
    }

    @Override
    public CompletableFuture<Snapshot> query(UUID player, ModeKey modeKey, StatWindow window) {
        return store().mode(modeKey).thenCompose(found -> {
            StatsMode mode = found.orElseThrow(() -> new IllegalArgumentException("Unbekannter Spielmodus: "
                    + modeKey));
            Optional<StatPeriod> period = window.period().filter(ignored -> mode.hasLeaderboard());
            CompletableFuture<OptionalLong> position = period.isPresent()
                    ? store().position(player, modeKey, period.get())
                    : CompletableFuture.completedFuture(OptionalLong.empty());
            return store().line(player, modeKey, window).thenCombine(position, (line, rank) ->
                    new Snapshot(mode, line, rank, StatsReport.of(mode, line, rank)));
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> byRank(ModeKey mode, StatPeriod period, int position) {
        return store().byRank(mode, period, position);
    }

    @Override
    public CompletableFuture<List<TopEntry>> top(ModeKey mode, StatPeriod period, int count) {
        return store().top(mode, period, count);
    }

    @Override
    public void sendStats(CommandSender viewer, ModeKey mode, StatWindow window, String target) {
        PaperCore context = context();
        if (catalog().mode(mode).isEmpty()) {
            texts().send(viewer, "stats.unknown-mode", Placeholder.unparsed("mode", mode.key()));
            return;
        }
        boolean bypass = viewer.hasPermission(StatsRules.BYPASS_PERMISSION);
        UUID self = viewer instanceof Player player ? player.getUniqueId() : null;
        CompletableFuture<Boolean> allowed = self == null ? CompletableFuture.completedFuture(true)
                : store().tryQuery(self);
        CompletableFuture<List<Component>> block = allowed.thenCompose(ok -> {
            if (!ok) {
                return message(viewer, "stats.cooldown");
            }
            return resolve(viewer, self, mode, window, target).thenCompose(resolved -> {
                if (resolved.error() != null) {
                    return message(viewer, resolved.error(), resolved.args());
                }
                UUID uuid = resolved.uuid();
                return store().hidden(uuid).thenCompose(hidden -> {
                    if (!StatsRules.mayView(self == null ? uuid : self, uuid, hidden, bypass || self == null)) {
                        return message(viewer, "stats.hidden");
                    }
                    return context.services().get(ProfileService.class).nameOf(uuid).thenCombine(
                            query(uuid, mode, window), (name, snapshot) -> StatsTexts.statsBlock(texts().i18n(),
                                    texts().locale(viewer), name.orElse(target), window, snapshot.report()));
                });
            });
        }).exceptionally(error -> {
            context.log().warn("/stats fehlgeschlagen", error);
            return List.of(texts().render(viewer, "error.data"));
        });
        context.mainThread().accept(block, "/stats", lines -> lines.forEach(viewer::sendMessage));
    }

    private CompletableFuture<Resolved> resolve(CommandSender viewer, @Nullable UUID self, ModeKey mode,
            StatWindow window, String target) {
        if (target.isEmpty()) {
            return CompletableFuture.completedFuture(self == null ? Resolved.error("stats.notfound")
                    : Resolved.of(self));
        }
        StatsArgs.Request request = new StatsArgs.Request(mode, window, target);
        OptionalInt rank = request.rank();
        if (target.startsWith("#")) {
            if (rank.isEmpty()) {
                return CompletableFuture.completedFuture(Resolved.error("stats.notfound"));
            }
            StatPeriod period = window.period().orElse(StatPeriod.DAYS_30);
            return store().byRank(mode, period, rank.getAsInt()).thenApply(found -> found.map(Resolved::of)
                    .orElseGet(() -> Resolved.error("stats.rank-empty", Placeholder.unparsed("n",
                            Integer.toString(rank.getAsInt())))));
        }
        return context().services().get(ProfileService.class).uuidOf(target).thenApply(found -> found
                .map(Resolved::of).orElseGet(() -> Resolved.error("stats.notfound")));
    }

    /** Aufgelöstes Ziel oder Fehler. */
    private record Resolved(@Nullable UUID uuidOrNull, @Nullable String error,
            List<TagResolver> argList) {

        static Resolved of(UUID uuid) {
            return new Resolved(uuid, null, List.of());
        }

        static Resolved error(String key, TagResolver... args) {
            return new Resolved(null, key, List.of(args));
        }

        UUID uuid() {
            if (uuidOrNull == null) {
                throw new IllegalStateException("kein Ziel");
            }
            return uuidOrNull;
        }

        TagResolver[] args() {
            return argList.toArray(TagResolver[]::new);
        }
    }

    private CompletableFuture<List<Component>> message(CommandSender viewer, String key,
            TagResolver... args) {
        return CompletableFuture.completedFuture(List.of(texts().render(viewer, key, args)));
    }

    @Override
    public void roundSummary(Player player, RoundSummary summary) {
        StatsTexts.roundBlock(texts().i18n(), texts().locale(player), summary).forEach(player::sendMessage);
    }

    @Override
    public ServerMode serverMode() {
        ServerMode set = override;
        if (set != null) {
            return set;
        }
        PaperCore context = context();
        return ServerMode.resolve(context.config().template(), context.config().category(),
                key -> catalog().mode(key).isPresent());
    }

    @Override
    public void serverMode(ServerMode mode) {
        override = mode;
    }

    // ------------------------------------------------------------------------------------------ /togglestats

    private void toggleStats(Player player) {
        UUID uuid = player.getUniqueId();
        Instant now = Instant.now();
        Instant last = toggles.get(uuid);
        if (last != null && Duration.between(last, now).compareTo(StatsRules.TOGGLE_COOLDOWN) < 0) {
            return;
        }
        toggles.put(uuid, now);
        Optional<ProfileView> view = context().services().find(PlayerProfiles.class).flatMap(p -> p.view(uuid));
        long playtime = view.map(found -> found.playtimeSeconds(now)).orElse(0L);
        if (!StatsRules.mayToggle(playtime)) {
            texts().send(player, "togglestats.requirement");
            return;
        }
        CompletableFuture<Boolean> toggled = store().hidden(uuid).thenCompose(hidden -> store().setHidden(uuid,
                !hidden));
        context().mainThread().accept(toggled.exceptionally(error -> null), "/togglestats", hidden -> {
            if (hidden == null) {
                texts().send(player, "error.data");
                return;
            }
            texts().send(player, hidden ? "togglestats.hidden" : "togglestats.visible");
            context().services().find(PlayerProfiles.class).ifPresent(p -> p.refresh(uuid,
                    List.of(Profile.STATS_HIDDEN)));
        });
    }

    // ------------------------------------------------------------------------------------------ /top

    private void top(Player player, StatPeriod period) {
        Optional<StatsMode> mode = serverMode().mode().flatMap(catalog()::mode);
        if (mode.isEmpty()) {
            texts().send(player, "stats.need-mode");
            return;
        }
        if (!mode.get().hasLeaderboard()) {
            texts().send(player, "top.none");
            return;
        }
        ModeKey key = mode.get().key();
        CompletableFuture<List<Component>> block = store().top(key, period, StatsRules.TOP_SIZE)
                .thenCombine(store().position(player.getUniqueId(), key, period), (entries, self) ->
                        StatsTexts.topBlock(texts().i18n(), texts().locale(player), mode.get(), period, entries, self))
                .exceptionally(error -> List.of(texts().render(player, "error.data")));
        context().mainThread().accept(block, "/top", lines -> lines.forEach(player::sendMessage));
    }

    // ------------------------------------------------------------------------------------------ /resetstats

    private void openReset(Player player) {
        ServerMode server = serverMode();
        Optional<StatsMode> mode = server.mode().flatMap(catalog()::mode);
        UUID uuid = player.getUniqueId();
        CompletableFuture<Long> tokens = context().services().get(WalletService.class).wallet(uuid)
                .thenApply(wallet -> wallet.resetTokens());
        context().mainThread().accept(tokens.exceptionally(error -> -1L), "/resetstats", count -> {
            if (count < 0) {
                texts().send(player, "error.data");
                return;
            }
            Optional<String> error = StatsRules.resetError(mode, server.modeLobby(), count);
            if (error.isPresent()) {
                texts().send(player, error.get());
                return;
            }
            StatsMode found = mode.orElseThrow();
            context().mainThread().accept(store().openReset(uuid, found.key()), "/resetstats", opened -> {
                dialogs.put(uuid, Instant.now().plus(Duration.ofSeconds(30)));
                Locale locale = texts().locale(player);
                I18n i18n = texts().i18n();
                player.sendMessage(i18n.render(locale, "reset.warn", ColorText.styling("mode_color", found.color()),
                        Placeholder.component("mode", StatsTexts.modeComponent(locale, found))));
                player.sendMessage(i18n.render(locale, "reset.ask", Placeholder.component("yes",
                        i18n.render(locale, "reset.button.yes")), Placeholder.component("no",
                        i18n.render(locale, "reset.button.no"))));
            });
        });
    }

    private void confirmReset(Player player) {
        UUID uuid = player.getUniqueId();
        dialogs.remove(uuid);
        CompletableFuture<Optional<ResetOutcome>> outcome = store().takeReset(uuid).thenCompose(mode -> mode
                .isEmpty() ? CompletableFuture.completedFuture(Optional.<ResetOutcome>empty())
                : store().reset(uuid, mode.get(), ResetScope.PLAYER, null)
                        .thenApply(result -> Optional.of(new ResetOutcome(mode.get(), result))));
        context().mainThread().accept(outcome.exceptionally(error -> Optional.of(new ResetOutcome(null, null))),
                "Statsreset", done -> {
                    if (done.isEmpty()) {
                        texts().send(player, "reset.expired");
                        return;
                    }
                    ResetOutcome result = done.get();
                    if (result.result() == null || result.mode() == null) {
                        texts().send(player, "error.data");
                        return;
                    }
                    switch (result.result()) {
                        case DONE -> {
                            String name = catalog().mode(result.mode()).map(found -> StatsTexts.modeName(
                                    texts().locale(player), found)).orElse(result.mode().key());
                            texts().send(player, "reset.done", Placeholder.unparsed("mode", name));
                            Bukkit.getPluginManager().callEvent(new StatsResetEvent(uuid, result.mode(),
                                    ResetScope.PLAYER));
                        }
                        case NO_TOKEN -> texts().send(player, "reset.no-token");
                        case NOT_RESETTABLE -> texts().send(player, "reset.not-resettable");
                        case UNKNOWN_MODE -> texts().send(player, "stats.unknown-mode",
                                Placeholder.unparsed("mode", result.mode().key()));
                    }
                });
    }

    /** Ausgang eines Resets ({@code null} = Datenfehler). */
    private record ResetOutcome(@Nullable ModeKey mode, @Nullable ResetResult result) {
    }

    private void cancelReset(Player player) {
        UUID uuid = player.getUniqueId();
        dialogs.remove(uuid);
        context().mainThread().accept(store().takeReset(uuid).exceptionally(error -> Optional.empty()),
                "Statsreset abbrechen", mode -> texts().send(player, mode.isPresent() ? "reset.cancel"
                        : "reset.expired"));
    }

    /** Chat-Antwort „Ja“/„Nein“ bei offenem Dialog; die Nachricht geht nicht an andere (Spec 02 §6.9 Nr. 3). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Instant until = dialogs.get(uuid);
        if (until == null) {
            return;
        }
        if (Instant.now().isAfter(until)) {
            dialogs.remove(uuid);
            return;
        }
        Optional<Boolean> answer = StatsRules.chatAnswer(PlainTextComponentSerializer.plainText()
                .serialize(event.message()));
        if (answer.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        context().mainThread().execute(() -> {
            if (answer.get()) {
                confirmReset(player);
            } else {
                cancelReset(player);
            }
        });
    }

    // ------------------------------------------------------------------------------------------ /statsadmin

    private void adminReset(CommandSender sender, String name, String modeInput) {
        Optional<StatsMode> mode = catalog().find(modeInput);
        if (mode.isEmpty()) {
            texts().send(sender, "stats.unknown-mode", Placeholder.unparsed("mode", modeInput));
            return;
        }
        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        CompletableFuture<Optional<ResetResult>> reset = context().services().get(ProfileService.class)
                .uuidOf(name).thenCompose(found -> found.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<ResetResult>empty())
                        : store().reset(found.get(), mode.get().key(), ResetScope.STAFF, actor).thenApply(result -> {
                            if (result == ResetResult.DONE) {
                                context().mainThread().execute(() -> Bukkit.getPluginManager().callEvent(
                                        new StatsResetEvent(found.get(), mode.get().key(), ResetScope.STAFF)));
                            }
                            return Optional.of(result);
                        }));
        context().mainThread().accept(reset.exceptionally(error -> Optional.of(ResetResult.UNKNOWN_MODE)),
                "/statsadmin reset", result -> {
                    if (result.isEmpty()) {
                        texts().send(sender, "stats.notfound");
                    } else if (result.get() == ResetResult.DONE) {
                        texts().send(sender, "statsadmin.reset-done", Placeholder.unparsed("name", name),
                                Placeholder.unparsed("mode", StatsTexts.modeName(texts().locale(sender), mode.get())));
                    } else {
                        texts().send(sender, "error.data");
                    }
                });
    }

    private void adminRebuild(CommandSender sender, String modeInput) {
        List<StatsMode> modes;
        if (modeInput.equalsIgnoreCase("all")) {
            modes = catalog().all().stream().filter(StatsMode::hasLeaderboard).toList();
        } else {
            Optional<StatsMode> mode = catalog().find(modeInput);
            if (mode.isEmpty()) {
                texts().send(sender, "stats.unknown-mode", Placeholder.unparsed("mode", modeInput));
                return;
            }
            modes = List.of(mode.get());
        }
        for (StatsMode mode : modes) {
            context().mainThread().accept(store().rebuild(mode.key()).exceptionally(error -> -1L), "Neuaufbau",
                    count -> {
                        if (count < 0) {
                            texts().send(sender, "error.data");
                            return;
                        }
                        texts().send(sender, "statsadmin.rebuild-done", Placeholder.unparsed("mode",
                                StatsTexts.modeName(texts().locale(sender), mode)), texts().number(sender, "n", count));
                    });
        }
    }

    // ------------------------------------------------------------------------------------------ Jobs

    /** Prüft jede Sekunde (asynchron) die Uhrzeit in Europe/Berlin; die Sperren halten einen Lauf je Tag. */
    private void runJobs() {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(NetworkTime.ZONE);
        LocalDate today = now.toLocalDate();
        StatsStore store = context.services().get(StatsStore.class);
        if (!today.equals(housekept)) {
            housekept = today;
            store.housekeeping(today).whenComplete((ignored, error) -> log(context, "Partitionen", error));
        }
        if (!today.equals(rolledOver) && !now.toLocalTime().isBefore(RollingWindow.ROLLOVER_AT)) {
            rolledOver = today;
            store.rollover(today).whenComplete((ignored, error) -> log(context, "Rollover", error));
        }
        if (!today.equals(reconciled) && !now.toLocalTime().isBefore(RollingWindow.RECONCILE_AT)) {
            reconciled = today;
            store.reconcile(today).whenComplete((ignored, error) -> log(context, "Reconcile", error));
        }
    }

    private static void log(PaperCore context, String job, @Nullable Throwable error) {
        if (error != null) {
            context.log().error("Statistik-Job {} fehlgeschlagen", job, error);
        }
    }

    // ------------------------------------------------------------------------------------------ Statistik-Profil

    @Override
    public void openProfile(Player viewer, UUID target, @Nullable Runnable back) {
        ProfileState state = new ProfileState(target, back, Map.of(), StatPeriod.DAYS_30);
        profiles.put(viewer.getUniqueId(), state);
        renderProfile(viewer, state);
    }

    private void renderProfile(Player viewer, ProfileState state) {
        PaperCore context = context();
        UUID target = state.target();
        boolean own = viewer.getUniqueId().equals(target);
        boolean bypass = viewer.hasPermission(StatsRules.BYPASS_PERMISSION);
        List<StatsMode> modes = catalog().all().stream().filter(StatsMode::active)
                .filter(mode -> !mode.fields().isEmpty()).toList();
        CompletableFuture<Optional<Profile>> profile = context.services().get(ProfileService.class).profile(target);
        CompletableFuture<Boolean> hidden = store().hidden(target);
        CompletableFuture<StatsProfileGui.View> view = profile.thenCompose(found -> hidden.thenCompose(isHidden -> {
            boolean mayView = StatsRules.mayView(viewer.getUniqueId(), target, isHidden, bypass);
            Map<ModeKey, CompletableFuture<Snapshot>> queries = new HashMap<>();
            if (mayView) {
                for (StatsMode mode : modes) {
                    StatPeriod period = state.periods().getOrDefault(mode.key(), state.global());
                    queries.put(mode.key(), query(target, mode.key(), StatWindow.of(period)));
                }
            }
            return CompletableFuture.allOf(queries.values().toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                Map<ModeKey, StatsReport> reports = new HashMap<>();
                queries.forEach((key, query) -> reports.put(key, query.join().report()));
                RankDisplay rank = context.services().find(RankDisplayService.class).map(service -> service.of(target))
                        .orElseGet(() -> RankDisplay.of(Rank.DEFAULT));
                long playtime = context.services().find(PlayerProfiles.class).flatMap(p -> p.view(target))
                        .map(online -> online.playtimeSeconds(Instant.now()))
                        .orElse(found.map(Profile::playtimeS).orElse(0L));
                String name = found.map(Profile::name).orElse(target.toString().substring(0, 8));
                return new StatsProfileGui.View(target, name, rank, playtime, found.map(Profile::coins).orElse(0L),
                        isHidden, mayView, own, modes, reports, state.periods(), state.global());
            });
        }));
        context.mainThread().accept(view, "Statistik-Profil", loaded -> {
            if (!viewer.isOnline() || profiles.get(viewer.getUniqueId()) != state) {
                return;
            }
            StatsProfileGui.Actions actions = new StatsProfileGui.Actions(
                    mode -> update(viewer, state, mode),
                    mode -> sendStats(viewer, mode, StatWindow.of(state.periods().getOrDefault(mode,
                            state.global())), own ? "" : loaded.name()),
                    () -> update(viewer, state, null),
                    () -> {
                        viewer.closeInventory();
                        toggleStats(viewer);
                    },
                    state.back());
            context.services().get(GuiService.class).open(viewer, StatsProfileGui.build(texts().i18n(),
                    texts().locale(viewer), loaded, actions));
        });
    }

    /** Wechselt den Zeitraum eines Items ({@code mode}) oder aller Items ({@code null}) und zeichnet neu. */
    private void update(Player viewer, ProfileState state, @Nullable ModeKey mode) {
        ProfileState next;
        if (mode == null) {
            next = new ProfileState(state.target(), state.back(), Map.of(), flip(state.global()));
        } else {
            Map<ModeKey, StatPeriod> periods = new HashMap<>(state.periods());
            periods.put(mode, flip(periods.getOrDefault(mode, state.global())));
            next = new ProfileState(state.target(), state.back(), periods, state.global());
        }
        profiles.put(viewer.getUniqueId(), next);
        renderProfile(viewer, next);
    }

    private static StatPeriod flip(StatPeriod period) {
        return period == StatPeriod.ALL ? StatPeriod.DAYS_30 : StatPeriod.ALL;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dialogs.remove(uuid);
        toggles.remove(uuid);
        profiles.remove(uuid);
    }
}
