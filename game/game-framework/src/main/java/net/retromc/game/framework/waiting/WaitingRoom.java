package net.retromc.game.framework.waiting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyService;
import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.event.PlayerJoinGameEvent;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapGui;
import net.retromc.game.framework.phase.CountdownPlan;
import net.retromc.game.framework.phase.PhaseTimer;
import net.retromc.game.framework.spectate.SpectatorService;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.game.framework.team.TeamAllocator;
import net.retromc.game.framework.team.TeamGui;
import net.retromc.paper.core.bar.ActionbarService;
import net.retromc.paper.core.board.BoardService;
import net.retromc.paper.core.board.BoardSpec;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.hotbar.HotbarService;
import net.retromc.paper.core.stats.StatsService;
import net.retromc.paper.core.title.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Wartelobby und Lobby-Countdown (Spec 03 §6.5, §4.2, §4.5, §4.6, §4.10, §8.1 {@code WaitingRoom}): Join-Ablauf,
 * Hotbar, Board, Actionbar, Tipps, Countdown im XP-Balken, {@code /start}, Teamzuteilung und Start.
 */
public final class WaitingRoom {

    /** Höchstdauer, die der Countdown bei 1 s auf die Map wartet (Spec 03 §6.5 Nr. 5). */
    public static final int MAP_HOLD_SECONDS = 15;
    /** Countdown bei {@code /game forcestart} (Spec 03 §3 #14). */
    public static final int FORCESTART_SECONDS = 5;

    private final Game game;
    private final CountdownPlan plan;
    private final PhaseTimer timer;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private @Nullable TipRotation<String> tips;
    private @Nullable BukkitTask tipTask;
    private @Nullable BukkitTask holdTask;
    private boolean forced;
    private boolean allocated;

    public WaitingRoom(Game game) {
        this.game = Objects.requireNonNull(game, "game");
        this.plan = CountdownPlan.lobby(game.settings().countdown(), game.mode().countdownAnnouncements());
        this.timer = new PhaseTimer(game.plugin());
    }

    public CountdownPlan plan() {
        return plan;
    }

    /** Restsekunden: im Countdown der Rest, in WAITING der volle Countdown. */
    public int rest() {
        return timer.running() ? timer.remaining() : plan.total();
    }

    /** Teamwahl und Forcemap noch offen (Spec 03 §6.5 Schutz). */
    public boolean choicesOpen() {
        return game.lifecycle().waiting() && plan.choicesOpen(rest());
    }

    // ------------------------------------------------------------------------------------------ Start/Ende

    /** Startet Tipps; der Server ist bereit (Map geladen). */
    public void open() {
        List<String> keys = new ArrayList<>(game.mode().tipKeys());
        for (int i = 1; game.texts().i18n().has("tips." + game.definition().modeId() + "." + i); i++) {
            keys.add("tips." + game.definition().modeId() + "." + i);
        }
        if (!keys.isEmpty()) {
            tips = new TipRotation<>(keys, new Random());
            long ticks = game.settings().tipInterval() * 20L;
            tipTask = Bukkit.getScheduler().runTaskTimer(game.plugin(), this::tip, ticks, ticks);
        }
    }

    public void close() {
        timer.cancel();
        BukkitTask task = tipTask;
        if (task != null) {
            task.cancel();
        }
        BukkitTask hold = holdTask;
        if (hold != null) {
            hold.cancel();
        }
        tipTask = null;
        holdTask = null;
    }

    // ------------------------------------------------------------------------------------------ Join/Quit

    /** Join in WAITING/STARTING (Spec 03 §6.5 Nr. 1–2). */
    public void join(Player player) {
        reset(player);
        if (game.maps().ready()) {
            player.teleport(game.maps().waitingSpawn());
        }
        GameTexts texts = game.texts();
        texts.broadcast("common.join", texts.name("name", player));
        if (game.mode().teamingForbidden()) {
            texts.send(player, "common.teaming.warn");
        }
        sendOnline(player);
        game.sendMapLine(player);
        if (tips != null) {
            sendTip(player, tips.next());
        }
        if (game.state() == GameState.STARTING) {
            announce(player, timer.remaining());
        }
        apply(player);
        fetchParty(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new PlayerJoinGameEvent(player, false));
        refreshAll();
        if (game.state() == GameState.STARTING && online() >= game.maxPlayers()
                && plan.shortens(timer.remaining())) {
            timer.set(plan.next(timer.remaining(), CountdownPlan.Event.FULL));
        }
        checkStart();
    }

    /** Quit in WAITING/STARTING (Spec 03 §6.5 Nr. 7). */
    public void quit(Player player) {
        game.texts().broadcast("common.quit", game.texts().name("name", player));
        game.teams().leave(player.getUniqueId());
        parties.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(game.plugin(), () -> {
            refreshAll();
            if (game.state() == GameState.STARTING && !forced && online() < game.minPlayers()
                    && plan.cancels(timer.remaining())) {
                cancelCountdown();
            }
        });
    }

    /** Hotbar, Board und Actionbar der Wartelobby. */
    public void apply(Player player) {
        game.services().find(HotbarService.class).ifPresent(hotbar -> hotbar.apply(player, hotbar(player)));
        game.services().find(BoardService.class).ifPresent(board -> board.show(player, board(player)));
        actionbar(player);
        player.sendExperienceChange(0f, 0);
    }

    /** Teleportiert alle Wartenden neu (nach Map-Wechsel). */
    public void relocate() {
        if (!game.maps().ready()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(game.maps().waitingSpawn());
        }
        refreshAll();
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            actionbar(player);
            game.services().find(BoardService.class).ifPresent(board -> board.refresh(player));
        }
        game.refreshTeamGuis();
    }

    private static void reset(Player player) {
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(health == null ? 20.0 : health.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void sendOnline(Player player) {
        game.texts().send(player, "common.online", Placeholder.unparsed("n", Integer.toString(online())),
                Placeholder.unparsed("cur", Integer.toString(online())),
                Placeholder.unparsed("max", Integer.toString(game.maxPlayers())));
    }

    private void fetchParty(UUID player) {
        game.services().find(PartyService.class).ifPresent(service -> service.partyOf(player)
                .thenAccept(party -> party.ifPresent(found -> parties.put(player, found)))
                .exceptionally(error -> null));
    }

    private int online() {
        return Bukkit.getOnlinePlayers().size();
    }

    // ------------------------------------------------------------------------------------------ Countdown

    /** Startet den Countdown, wenn genug Spieler da sind und die Map geladen ist. */
    public void checkStart() {
        if (game.state() == GameState.WAITING && game.maps().ready() && online() >= game.minPlayers()) {
            startCountdown(plan.total());
        }
    }

    private void startCountdown(int seconds) {
        game.lifecycle().transitionTo(GameState.STARTING);
        allocated = false;
        timer.start("countdown", Duration.ofSeconds(seconds), this::tick, this::countdownEnded);
    }

    private void tick(int rest) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendExperienceChange(plan.progress(rest), rest);
        }
        game.statePublisher().countdown(rest);
        if (plan.announce(rest)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                announce(player, rest);
                Sound sound = rest >= 10 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_PLING;
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
        if (game.settings().countdownTitles() && rest >= 1 && rest <= 3) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                game.services().find(TitleService.class).ifPresent(titles -> titles.show(player,
                        "game.title.countdown", null, TitleService.ticks(0, 20, 0),
                        Placeholder.unparsed("n", Integer.toString(rest))));
            }
        }
        if (rest <= CountdownPlan.LOCK) {
            game.maps().finishVoteOnce();
        }
        if (!allocated && rest <= Math.max(0, game.mode().allocateAt()) && rest > 0) {
            allocate();
        }
        if (rest == CountdownPlan.LOCK) {
            game.refreshTeamGuis();
        }
    }

    private void announce(Player player, int rest) {
        game.texts().send(player, game.texts().count("common.countdown", rest),
                Placeholder.unparsed("n", Integer.toString(rest)));
    }

    private void countdownEnded() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        if (game.maps().ready()) {
            begin();
            return;
        }
        int[] waited = {0};
        holdTask = Bukkit.getScheduler().runTaskTimer(game.plugin(), () -> {
            waited[0]++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendExperienceChange(plan.progress(1), 1);
            }
            if (game.maps().ready()) {
                cancelHold();
                begin();
            } else if (waited[0] >= MAP_HOLD_SECONDS) {
                cancelHold();
                game.abortMapLoad();
            }
        }, 20L, 20L);
    }

    private void cancelHold() {
        BukkitTask task = holdTask;
        if (task != null) {
            task.cancel();
        }
        holdTask = null;
    }

    private void begin() {
        // erneut: verteilt auch Spieler, die nach der Zuteilung bei 10 s noch beigetreten sind
        allocate();
        close();
        game.beginRound();
    }

    private void cancelCountdown() {
        timer.cancel();
        game.lifecycle().transitionTo(GameState.WAITING);
        game.statePublisher().countdown(-1);
        game.texts().broadcast("common.countdown.cancel");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendExperienceChange(0f, 0);
        }
        refreshAll();
    }

    /** Spieler ohne Team verteilen (Spec 03 §6.6), Partys nach {@code PartyService}. */
    public void allocate() {
        allocated = true;
        if (game.definition().teamMode() == TeamMode.SOLO) {
            return;
        }
        List<UUID> unteamed = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId)
                .filter(uuid -> game.teams().teamOf(uuid) == null).toList();
        Map<UUID, String> partyOf = new java.util.HashMap<>();
        parties.forEach((member, party) -> partyOf.put(member, party.id()));
        TeamAllocator.Allocation allocation = game.teams().allocate(unteamed, partyOf, game.mode().allocation());
        for (UUID split : allocation.split()) {
            Player player = Bukkit.getPlayer(split);
            if (player != null) {
                game.texts().send(player, "common.team.party_split");
            }
        }
        refreshAll();
    }

    /**
     * Party-Leiter zieht seine Party mit ins gewählte Team, wenn Platz ist (Spec 04 §5.3, Modi mit
     * {@code partyFollowsLeader}).
     */
    public void pullParty(Player leader, GameTeam team) {
        Party party = parties.get(leader.getUniqueId());
        if (!game.mode().partyFollowsLeader() || party == null || !party.leader().equals(leader.getUniqueId())) {
            return;
        }
        List<Player> members = party.memberIds().stream().filter(id -> !id.equals(leader.getUniqueId()))
                .map(Bukkit::getPlayer).filter(Objects::nonNull).filter(member -> !team.contains(member.getUniqueId()))
                .toList();
        if (members.isEmpty() || team.capacity() - team.size() < members.size()) {
            return;
        }
        for (Player member : members) {
            game.teams().assign(member.getUniqueId(), team);
            game.texts().send(member, "common.team.chosen", game.texts().team(game.texts().locale(member), team));
        }
    }

    /** {@code /start} (Spec 03 §3 #4); {@code owner} = Besitzer der Privatrunde (ohne Mindestspieler). */
    public void startCommand(Player player, boolean owner) {
        GameTexts texts = game.texts();
        if (!game.lifecycle().waiting()) {
            texts.send(player, "common.not_in_lobby");
            return;
        }
        if (!owner && online() < game.minPlayers()) {
            texts.send(player, "common.start.too_few", Placeholder.unparsed("n", Integer.toString(online())),
                    Placeholder.unparsed("min", Integer.toString(game.minPlayers())));
            return;
        }
        if (game.state() == GameState.STARTING && !plan.shortens(timer.remaining())) {
            texts.send(player, "common.start.already");
            return;
        }
        if (game.state() == GameState.WAITING) {
            if (!game.maps().ready()) {
                texts.send(player, "common.start.already");
                return;
            }
            forced = owner;
            startCountdown(CountdownPlan.SHORTENED);
        } else {
            timer.set(plan.next(timer.remaining(), CountdownPlan.Event.START_CMD));
        }
        texts.send(player, "common.start.done");
        texts.broadcast("common.start.broadcast", texts.name("name", player));
    }

    /** {@code /game forcestart}: sofort 5 s ohne Mindestspieler. */
    public boolean forceStart() {
        if (!game.lifecycle().waiting() || !game.maps().ready()) {
            return false;
        }
        forced = true;
        if (game.state() == GameState.WAITING) {
            startCountdown(FORCESTART_SECONDS);
        } else {
            timer.set(Math.min(timer.remaining(), FORCESTART_SECONDS));
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ Tipps

    private void tip() {
        TipRotation<String> rotation = tips;
        if (rotation == null || !game.lifecycle().waiting()
                || (game.state() == GameState.STARTING && timer.remaining() <= CountdownPlan.LOCK)) {
            return;
        }
        String key = rotation.next();
        Bukkit.getOnlinePlayers().forEach(player -> sendTip(player, key));
    }

    private void sendTip(Player player, String key) {
        GameTexts texts = game.texts();
        if (key.startsWith("tips.")) {
            texts.send(player, "common.tip", Placeholder.component("tip",
                    texts.i18n().render(texts.locale(player), key)));
        } else {
            texts.send(player, key);
        }
    }

    // ------------------------------------------------------------------------------------------ Anzeige

    /** Actionbar (Spec 03 §4.5): Team, „Wähle dein Team“ oder „Warten auf Spieler“. */
    public void actionbar(Player player) {
        ActionbarService bar = game.services().find(ActionbarService.class).orElse(null);
        if (bar == null) {
            return;
        }
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        GameTeam team = game.teams().teamOf(player.getUniqueId());
        Component text;
        if (team != null && game.variant().teamPlay()) {
            text = texts.i18n().render(locale, "game.team.label", texts.team(locale, team));
        } else if (game.variant().teamPlay() && game.definition().teamMode() != TeamMode.SOLO) {
            text = texts.i18n().render(locale, "game.actionbar.choose");
        } else if (online() < game.minPlayers()) {
            text = texts.i18n().render(locale, "common.waiting", Placeholder.unparsed("n", Integer.toString(online())),
                    Placeholder.unparsed("min", Integer.toString(game.minPlayers())));
        } else {
            bar.clear(player, SpectatorService.ACTIONBAR);
            return;
        }
        bar.set(player, SpectatorService.ACTIONBAR, ActionbarService.Priority.PERSISTENT, text, 0);
    }

    /** Wartelobby-Board (Spec 03 §4.2, Scores 8..0). */
    public BoardSpec board(Player player) {
        GameTexts texts = game.texts();
        UUID uuid = player.getUniqueId();
        java.util.function.Supplier<Locale> locale = () -> texts.i18n().locale(uuid);
        return BoardSpec.builder()
                .title(() -> texts.i18n().render(locale.get(), "sb.title"))
                .blank()
                .line("map", () -> texts.i18n().render(locale.get(), "game.board.map"))
                .line("map-value", () -> texts.i18n().render(locale.get(), "game.board.map.value",
                        Placeholder.unparsed("map", game.maps().currentIfSet().map(GameMap::displayName)
                                .orElse("-"))))
                .blank()
                .line("block", () -> texts.i18n().render(locale.get(), game.mode().waitingBlock().labelKey()))
                .line("block-value", () -> game.mode().waitingBlock().value(game, player, locale.get()))
                .blank()
                .line("cooldown", () -> texts.i18n().render(locale.get(), "game.board.hitcooldown"))
                .line("cooldown-value", () -> texts.i18n().render(locale.get(),
                        game.combat() == CombatProfile.MODERN ? "game.board.hitcooldown.on"
                                : "game.board.hitcooldown.off"))
                .build();
    }

    /** Wartelobby-Hotbar (Spec 03 §4.10). */
    public HotbarLayout hotbar(Player player) {
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        HotbarLayout layout = HotbarLayout.create();
        if (game.variant().teamPlay() && game.definition().teamMode() != TeamMode.SOLO) {
            layout.slot(0, SpectatorService.item(texts, locale, game.mode().teamItem(), "game.hotbar.team"),
                    (clicker, right) -> game.openTeamGui(clicker));
        }
        layout.slot(1, SpectatorService.item(texts, locale, Material.PAPER, "game.hotbar.map"),
                (clicker, right) -> MapGui.open(game, clicker));
        layout.slot(6, SpectatorService.item(texts, locale, Material.NETHER_STAR, "game.hotbar.achievements"),
                (clicker, right) -> game.services().find(StatsService.class)
                        .ifPresent(stats -> stats.openProfile(clicker, clicker.getUniqueId(), null)));
        layout.slot(7, SpectatorService.item(texts, locale, Material.WRITTEN_BOOK, "game.hotbar.guide"),
                (clicker, right) -> openGuide(clicker));
        layout.slot(8, SpectatorService.item(texts, locale, Material.DIAMOND, "game.hotbar.cosmetics"),
                (clicker, right) -> texts.send(clicker, "game.cosmetics.soon"));
        game.mode().waitingHotbar(player, layout, locale);
        return layout;
    }

    /** Anleitungsbuch {@code guide.<mode>.title}, Seiten {@code guide.<mode>.page.N}. */
    public void openGuide(Player player) {
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        String base = "guide." + game.definition().modeId();
        List<Component> pages = new ArrayList<>();
        for (int i = 1; texts.i18n().has(base + ".page." + i); i++) {
            pages.add(texts.i18n().render(locale, base + ".page." + i));
        }
        String color = game.services().find(net.retromc.paper.core.stats.ModeCatalog.class)
                .flatMap(catalog -> catalog.mode(net.retromc.core.api.stats.ModeKey.of(game.definition().modeId())))
                .map(net.retromc.core.api.stats.StatsMode::color).orElse("white");
        player.openBook(Book.book(texts.i18n().render(locale, base + ".title", Placeholder.unparsed("mode",
                game.definition().displayName()), net.retromc.platform.common.text.ColorText.styling("mode_color",
                        color)), Component.text(BuildInfo.BRAND_NAME), pages));
    }

    /** Öffnet die Team-Wahl. */
    public void openTeamGui(Player player) {
        game.services().get(GuiService.class).open(player, game.trackTeamGui(TeamGui.create(game,
                game.texts().locale(player))));
    }
}
