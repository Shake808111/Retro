package net.retromc.game.framework;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.map.MapMeta;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.i18n.Messages;
import net.retromc.game.framework.chat.GameChat;
import net.retromc.game.framework.command.GameCommands;
import net.retromc.game.framework.end.GameResult;
import net.retromc.game.framework.end.RoundEnd;
import net.retromc.game.framework.event.GameStartEvent;
import net.retromc.game.framework.event.PlayerEliminatedEvent;
import net.retromc.game.framework.event.PlayerJoinGameEvent;
import net.retromc.game.framework.event.PlayerQuitGameEvent;
import net.retromc.game.framework.event.TeamJoinEvent;
import net.retromc.game.framework.kill.DamageTracker;
import net.retromc.game.framework.kill.KillFeed;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapSelection;
import net.retromc.game.framework.map.WaitingPlatform;
import net.retromc.game.framework.server.PrivateRoundContext;
import net.retromc.game.framework.server.ServerStatePublisher;
import net.retromc.game.framework.spectate.SpectatorReason;
import net.retromc.game.framework.spectate.SpectatorService;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.game.framework.team.TeamGui;
import net.retromc.game.framework.team.TeamRegistry;
import net.retromc.game.framework.waiting.WaitingRoom;
import net.retromc.paper.core.bar.ActionbarService;
import net.retromc.paper.core.board.BoardService;
import net.retromc.paper.core.hotbar.HotbarService;
import net.retromc.paper.core.server.PlayerTransfer;
import net.retromc.paper.core.server.ServerLifecycle;
import net.retromc.paper.core.stats.ServerMode;
import net.retromc.paper.core.stats.StatsService;
import net.retromc.paper.core.tab.NametagService;
import net.retromc.paper.core.tab.TabService;
import net.retromc.paper.core.title.TitleService;
import net.retromc.platform.common.concurrent.MainThreadExecutor;
import net.retromc.platform.common.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Eine Runde auf diesem Server (Spec 03 §6.5–§6.10): verbindet Wartelobby, Teams, Map, Zuschauer, Kill-Feed,
 * Rundenende und Server-Status. Ein Spielserver führt genau einen Modus und eine Runde aus (Spec 08 §6.10).
 * Alle Kern-Dienste kommen bei jedem Gebrauch aus der {@link ServiceRegistry} und sind damit austauschbar.
 */
public final class Game {

    /** Angriffsgeschwindigkeit ohne 1.9-Cooldown ({@link CombatProfile#LEGACY_1_8}). */
    static final double LEGACY_ATTACK_SPEED = 1024.0;
    /** Vanilla-Angriffsgeschwindigkeit. */
    static final double MODERN_ATTACK_SPEED = 4.0;

    private final MiniGamePlugin plugin;
    private final GameDefinition definition;
    private final GameSettings settings;
    private final ServiceRegistry services;
    private final GameVariant variant;
    private final PrivateRoundContext privateRound;
    private final GameLifecycle lifecycle = new GameLifecycle();
    private final UUID matchId = UUID.randomUUID();
    private final Map<UUID, GamePlayer> players = new ConcurrentHashMap<>();
    private final DamageTracker damage = new DamageTracker();
    private final MainThreadExecutor mainThread;
    private final List<Registration> registrations = new ArrayList<>();
    private final Set<TeamGui> teamGuis = Collections.newSetFromMap(new WeakHashMap<>());
    private @Nullable GameMode mode;
    private @Nullable GameTexts texts;
    private @Nullable TeamRegistry teams;
    private @Nullable MapSelection maps;
    private @Nullable WaitingRoom waiting;
    private @Nullable SpectatorService spectators;
    private @Nullable KillFeed killFeed;
    private @Nullable RoundEnd roundEnd;
    private @Nullable ServerStatePublisher publisher;
    private @Nullable StatefulRedisConnection<String, String> valkey;
    private volatile Instant startedAt = Instant.now();
    private volatile boolean mapOpened;
    private volatile CombatProfile combat;

    Game(MiniGamePlugin plugin, GameDefinition definition, GameSettings settings, ServiceRegistry services,
            GameVariant variant, PrivateRoundContext privateRound) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.services = Objects.requireNonNull(services, "services");
        this.variant = Objects.requireNonNull(variant, "variant");
        this.privateRound = Objects.requireNonNull(privateRound, "privateRound");
        this.combat = definition.defaultCombat();
        this.mainThread = new MainThreadExecutor(Bukkit::isPrimaryThread, task -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }, plugin.getSLF4JLogger());
    }

    /** Baut die Teilsysteme, registriert Listener, Befehle und Dienste und lädt die erste Map. */
    void enable(GameMode gameMode) {
        this.mode = Objects.requireNonNull(gameMode, "gameMode");
        this.teams = new TeamRegistry(definition.teamMode(), variant, gameMode.teamOrder());
        this.texts = new GameTexts(services, ModeKey.of(definition.modeId()), gameMode.keyOverrides(),
                uuid -> teams().teamOf(uuid), definition.teamMode() != TeamMode.SOLO);
        this.valkey = services.get(ValkeyConnector.class).connect();
        this.maps = new MapSelection(this);
        this.spectators = new SpectatorService(this);
        this.killFeed = new KillFeed(this);
        this.roundEnd = new RoundEnd(this);
        this.waiting = new WaitingRoom(this);
        this.publisher = new ServerStatePublisher(this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new GameChat(this), plugin);
        GameCommands.register(this);
        services.find(TabService.class).ifPresent(tab -> registrations.add(tab.registerEntryProvider(this::tabEntry)));
        services.find(StatsService.class).ifPresent(stats -> stats.serverMode(new ServerMode(
                Optional.of(ModeKey.of(definition.modeId())), false)));
        registrations.add(services.registerDefault(Game.class, this));
        publisher.attach();
        maps.init().whenComplete((map, error) -> {
            if (error != null) {
                log().error("Keine spielbare Map – Server bleibt BOOTING ({}). Maps mit /…setup einrichten.",
                        error.getMessage());
            }
        });
    }

    void disable() {
        WaitingRoom room = waiting;
        if (room != null) {
            room.close();
        }
        RoundEnd end = roundEnd;
        if (end != null) {
            end.cancel();
        }
        GameMode current = mode;
        if (current != null) {
            current.onDisable();
        }
        registrations.forEach(Registration::close);
        registrations.clear();
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection != null) {
            connection.close();
        }
        valkey = null;
    }

    // ------------------------------------------------------------------------------------------ Zugriff

    public MiniGamePlugin plugin() {
        return plugin;
    }

    public GameDefinition definition() {
        return definition;
    }

    public GameSettings settings() {
        return settings;
    }

    public ServiceRegistry services() {
        return services;
    }

    public GameVariant variant() {
        return variant;
    }

    public GameLifecycle lifecycle() {
        return lifecycle;
    }

    public GameState state() {
        return lifecycle.state();
    }

    /** ID der Runde ({@code stats.match.match_id}). */
    public UUID matchId() {
        return matchId;
    }

    public Logger log() {
        return plugin.getSLF4JLogger();
    }

    public MainThreadExecutor mainThread() {
        return mainThread;
    }

    public RedisAsyncCommands<String, String> redis() {
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection == null) {
            throw new IllegalStateException("Valkey nicht verbunden");
        }
        return connection.async();
    }

    public GameMode mode() {
        return require(mode);
    }

    public GameTexts texts() {
        return require(texts);
    }

    public TeamRegistry teams() {
        return require(teams);
    }

    public MapSelection maps() {
        return require(maps);
    }

    public WaitingRoom waiting() {
        return require(waiting);
    }

    public SpectatorService spectators() {
        return require(spectators);
    }

    public KillFeed killFeed() {
        return require(killFeed);
    }

    public RoundEnd roundEnd() {
        return require(roundEnd);
    }

    public ServerStatePublisher statePublisher() {
        return require(publisher);
    }

    public DamageTracker damage() {
        return damage;
    }

    public PrivateRoundContext privateRound() {
        return privateRound;
    }

    /** Kampfprofil der Runde (Map {@code combat} oder Standard des Modus). */
    public CombatProfile combat() {
        return combat;
    }

    /** Start der Runde (INGAME) bzw. Serverstart davor. */
    public Instant startedAt() {
        return startedAt;
    }

    /** Spielzeit seit dem Start. */
    public Duration elapsed() {
        return Duration.between(startedAt, Instant.now());
    }

    public int maxPlayers() {
        return privateRound.current().isPresent() ? variant.effectivePrivateMax() : variant.maxPlayers();
    }

    /** Mindestspieler; Privatrunden 2 (Besitzer startet mit {@code /start} auch allein). */
    public int minPlayers() {
        return privateRound.current().isPresent() ? Math.min(2, maxPlayers()) : variant.effectiveMinPlayers();
    }

    public int countdownRest() {
        return waiting().rest();
    }

    public String serverName() {
        return services.find(ServerLifecycle.class).map(lifecycle -> lifecycle.self().name())
                .orElse(Bukkit.getServer().getName());
    }

    public Optional<GamePlayer> player(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public Collection<GamePlayer> players() {
        return List.copyOf(players.values());
    }

    /** Lebende Spieler, die online sind. */
    public List<Player> alivePlayers() {
        List<Player> alive = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (player(online.getUniqueId()).map(GamePlayer::alive).orElse(false)) {
                alive.add(online);
            }
        }
        return alive;
    }

    /** Lebender, verbundener Spieler. */
    public boolean alive(UUID uuid) {
        return player(uuid).map(GamePlayer::alive).orElse(false) && Bukkit.getPlayer(uuid) != null;
    }

    // ------------------------------------------------------------------------------------------ Map

    /** Welt der Map ist geladen (aus {@link MapSelection}). */
    public void onMapLoaded(GameMap map, World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setTime(6000);
        world.setStorm(false);
        combat = map.combat().orElse(definition.defaultCombat());
        teams().build(map);
        mode().onMapReady(map, world);
        if (!mapOpened) {
            mapOpened = true;
            waiting().open();
            statePublisher().ready();
        } else {
            statePublisher().map(map.id());
        }
        Bukkit.getOnlinePlayers().forEach(this::applyCombat);
        if (lifecycle.waiting()) {
            waiting().relocate();
            waiting().checkStart();
        }
    }

    /** Sendet {@code common.map} (Spec 03 §7.2). */
    public void sendMapLine(Player player) {
        maps().currentIfSet().ifPresent(map -> texts().send(player, "common.map",
                Placeholder.unparsed("map", map.displayName()), Placeholder.unparsed("builders", map.builders()),
                Placeholder.unparsed("builder", map.builders())));
    }

    public void relocateWaiting() {
        waiting().relocate();
    }

    /** Map nicht rechtzeitig geladen (Spec 03 §6.5 Nr. 5): alle zurück in die Lobby, Server stoppt. */
    public void abortMapLoad() {
        texts().broadcast("common.map.loadfail");
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        services.find(PlayerTransfer.class).ifPresent(transfer -> transfer.toLobby(online, "game.ended",
                definition.modeId()));
        services.find(ServerLifecycle.class).ifPresent(lifecycle -> lifecycle.requestShutdown("MAP_LOAD_FAILED"));
    }

    /** Ort eines Spawns auf der Map. */
    public Location location(MapMeta.Spawn spawn) {
        return maps().location(spawn);
    }

    // ------------------------------------------------------------------------------------------ Teams

    /** Team-Wahl (GUI 5.10, {@code /team}). */
    public void chooseTeam(Player player, GameTeam team) {
        if (!lifecycle.waiting() || !variant.teamPlay() || definition.teamMode() == TeamMode.SOLO) {
            texts().send(player, "common.team.locked");
            return;
        }
        TeamJoinEvent event = new TeamJoinEvent(player, team);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        switch (teams().join(player.getUniqueId(), team, waiting().choicesOpen())) {
            case LOCKED -> texts().send(player, "common.team.locked");
            case FULL -> texts().send(player, "common.team.full");
            case SAME -> {
                return;
            }
            case OK -> {
                texts().send(player, "common.team.chosen", texts().team(texts().locale(player), team));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                waiting().pullParty(player, team);
            }
            default -> throw new IllegalStateException();
        }
        waiting().refreshAll();
    }

    public void leaveTeam(Player player) {
        if (!waiting().choicesOpen()) {
            texts().send(player, "common.team.locked");
            return;
        }
        teams().leave(player.getUniqueId());
        waiting().refreshAll();
    }

    public void openTeamGui(Player player) {
        waiting().openTeamGui(player);
    }

    public TeamGui trackTeamGui(TeamGui gui) {
        synchronized (teamGuis) {
            teamGuis.add(gui);
        }
        return gui;
    }

    public void refreshTeamGuis() {
        List<TeamGui> open;
        synchronized (teamGuis) {
            open = new ArrayList<>(teamGuis);
        }
        open.forEach(TeamGui::refresh);
    }

    // ------------------------------------------------------------------------------------------ Ablauf

    /** Countdown bei 0 (Spec 03 §6.5 Nr. 5–6): Teleport auf die Spawns, Inventar leeren, INGAME. */
    public void beginRound() {
        GameMap map = maps().current();
        startedAt = Instant.now();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<MapMeta.Spawn> soloSpawns = map.soloSpawns();
        int soloIndex = 0;
        List<Player> late = new ArrayList<>();
        for (Player player : online) {
            GamePlayer gamePlayer = players.computeIfAbsent(player.getUniqueId(),
                    uuid -> new GamePlayer(uuid, player.getName(), Instant.now()));
            GameTeam team = teams().teamOf(player.getUniqueId());
            if (definition.teamMode() == TeamMode.SOLO) {
                Optional<GameTeam> slot = teams().byIndex(soloIndex);
                if (slot.isPresent() && soloIndex < soloSpawns.size()) {
                    teams().assign(player.getUniqueId(), slot.get());
                    team = slot.get();
                }
            }
            Location spawn = spawnOf(map, team, soloSpawns, soloIndex);
            if (team == null || spawn == null) {
                late.add(player);
                continue;
            }
            soloIndex++;
            gamePlayer.team(team);
            gamePlayer.participant(true);
            gamePlayer.state(GamePlayer.State.PLAYING);
            prepare(player);
            player.teleport(spawn);
        }
        lifecycle.transitionTo(GameState.INGAME);
        int removed = WaitingPlatform.remove(map, maps().world(), log());
        if (removed > 0) {
            log().info("Wartelobby auf {} entfernt ({} Blöcke)", map.id(), removed);
        }
        for (Player player : late) {
            player.teleport(maps().spectatorSpawn());
            spectators().makeSpectator(player, SpectatorReason.LATE_JOIN, true);
        }
        for (Player player : online) {
            refreshTab(player);
            decorate(player);
            services.find(TitleService.class).ifPresent(titles -> titles.intro(player,
                    ModeKey.of(definition.modeId()), map.displayName()));
        }
        Bukkit.getPluginManager().callEvent(new GameStartEvent(matchId, map, teams().teams()));
        mode().onStart();
        checkWin();
    }

    private @Nullable Location spawnOf(GameMap map, @Nullable GameTeam team, List<MapMeta.Spawn> soloSpawns,
            int soloIndex) {
        if (team == null) {
            return null;
        }
        if (definition.teamMode() == TeamMode.SOLO) {
            return soloIndex < soloSpawns.size() ? location(soloSpawns.get(soloIndex)) : null;
        }
        return map.teamSpawn(team.id()).map(this::location).orElse(null);
    }

    /** Spieler für die Runde vorbereiten: Inventar leer, SURVIVAL, volle Leben, echte XP-Anzeige. */
    public void prepare(Player player) {
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(health == null ? 20.0 : health.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.sendExperienceChange(player.getExp(), player.getLevel());
        services.find(HotbarService.class).ifPresent(hotbar -> hotbar.clear(player));
        services.find(BoardService.class).ifPresent(board -> board.hide(player));
        applyCombat(player);
    }

    /** Nametag {@code {teamColor}{team} §7| §f{name}} und Team-Actionbar (Spec 03 §6.6). */
    public void decorate(Player player) {
        GameTeam team = teams().teamOf(player.getUniqueId());
        if (team == null || definition.teamMode() == TeamMode.SOLO) {
            return;
        }
        Locale locale = texts().locale(player);
        services.find(NametagService.class).ifPresent(nametags -> {
            nametags.prefix(player, texts().i18n().render(Messages.DEFAULT_LOCALE, "game.nametag.prefix",
                    texts().team(Messages.DEFAULT_LOCALE, team)));
            nametags.nameColor(player, NamedTextColor.WHITE);
        });
        if (variant.teamPlay()) {
            services.find(ActionbarService.class).ifPresent(bar -> bar.set(player, SpectatorService.ACTIONBAR,
                    ActionbarService.Priority.PERSISTENT, texts().i18n().render(locale, "game.team.label",
                            texts().team(locale, team)), 0));
        }
    }

    /** Angriffsgeschwindigkeit nach Kampfprofil (MODERN = 1.9-Cooldown, LEGACY_1_8 = ohne). */
    public void applyCombat(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) {
            speed.setBaseValue(combat == CombatProfile.MODERN ? MODERN_ATTACK_SPEED : LEGACY_ATTACK_SPEED);
        }
    }

    /** Endgültiges Ausscheiden: Zuschauer, {@code PlayerEliminatedEvent}, Siegprüfung. */
    public void eliminate(Player victim, @Nullable Player killer) {
        GamePlayer gamePlayer = players.get(victim.getUniqueId());
        if (gamePlayer == null || !gamePlayer.alive()) {
            return;
        }
        gamePlayer.state(GamePlayer.State.SPECTATOR);
        damage.clear(victim.getUniqueId());
        if (maps().ready()) {
            victim.teleport(maps().spectatorSpawn());
        }
        spectators().makeSpectator(victim, SpectatorReason.ELIMINATED, true);
        Bukkit.getPluginManager().callEvent(new PlayerEliminatedEvent(victim, killer, alivePlayers().size()));
        checkWin();
    }

    /** Sieg, wenn nur noch ein Team bzw. ein Spieler lebt; niemand → Abbruch. */
    public void checkWin() {
        if (state() != GameState.INGAME || roundEnd().finished()) {
            return;
        }
        boolean anyone = players.values().stream().anyMatch(player -> player.participant()
                && Bukkit.getPlayer(player.uuid()) != null);
        if (!anyone) {
            finish(GameResult.ofAbort());
            return;
        }
        if (definition.teamMode() == TeamMode.SOLO) {
            List<Player> alive = alivePlayers();
            if (alive.size() <= 1) {
                finish(alive.isEmpty() ? GameResult.ofDraw()
                        : GameResult.ofPlayer(alive.getFirst().getUniqueId(), List.of(
                                new GameResult.Placement(alive.getFirst().getUniqueId(), 1))));
            }
            return;
        }
        List<GameTeam> alive = teams().alive(this::alive);
        if (alive.size() <= 1) {
            finish(alive.isEmpty() ? GameResult.ofDraw() : GameResult.ofTeam(alive.getFirst()));
        }
    }

    /** Rundenende (Spec 03 §6.10), nur einmal wirksam. */
    public void finish(GameResult result) {
        roundEnd().finish(result);
    }

    // ------------------------------------------------------------------------------------------ Join/Quit

    void handleJoin(Player player) {
        applyCombat(player);
        GameState state = state();
        if (lifecycle.waiting()) {
            players.put(player.getUniqueId(), new GamePlayer(player.getUniqueId(), player.getName(), Instant.now()));
            waiting().join(player);
            return;
        }
        if (state == GameState.INGAME && mode().onLateJoin(player)) {
            return;
        }
        if (state == GameState.INGAME && !definition.spectatable()) {
            services.find(PlayerTransfer.class).ifPresent(transfer -> transfer.toLobby(
                    List.of(player.getUniqueId()), "game.running", definition.modeId()));
            return;
        }
        GamePlayer gamePlayer = players.computeIfAbsent(player.getUniqueId(),
                uuid -> new GamePlayer(uuid, player.getName(), Instant.now()));
        gamePlayer.state(GamePlayer.State.SPECTATOR);
        if (maps().ready()) {
            player.teleport(maps().spectatorSpawn());
        }
        spectators().makeSpectator(player, SpectatorReason.LATE_JOIN, true);
        Bukkit.getPluginManager().callEvent(new PlayerJoinGameEvent(player, true));
    }

    void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        GameState state = state();
        Bukkit.getPluginManager().callEvent(new PlayerQuitGameEvent(player, state));
        if (lifecycle.waiting()) {
            waiting().quit(player);
            players.remove(uuid);
            return;
        }
        GamePlayer gamePlayer = players.get(uuid);
        spectators().forget(uuid);
        damage.forget(uuid);
        if (state != GameState.INGAME || gamePlayer == null) {
            return;
        }
        if (gamePlayer.alive()) {
            if (!mode().onIngameQuit(player, gamePlayer)) {
                texts().broadcast("common.quit", texts().name("name", player));
            }
            gamePlayer.state(GamePlayer.State.LEFT);
            Bukkit.getPluginManager().callEvent(new PlayerEliminatedEvent(player, null,
                    alivePlayers().size() - 1));
        }
        Bukkit.getScheduler().runTask(plugin, this::checkWin);
    }

    // ------------------------------------------------------------------------------------------ Tab

    public void refreshTab(Player player) {
        services.find(TabService.class).ifPresent(tab -> tab.refresh(player));
    }

    /** Tab-Einträge (Spec 03 §4.3): Team {@code {teamColor}{team} §7| §f{name}}, Zuschauer grau kursiv unten. */
    private Optional<TabService.Entry> tabEntry(Player player) {
        if (state() != GameState.INGAME && state() != GameState.ENDING) {
            return Optional.empty();
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        String name = texts().displayName(player.getUniqueId(), player.getName());
        if (spectators().isSpectator(player)) {
            return Optional.of(new TabService.Entry(texts().i18n().render(locale, "game.tab.spectator",
                    Placeholder.unparsed("name", name)), 0));
        }
        GameTeam team = teams().teamOf(player.getUniqueId());
        if (team == null || definition.teamMode() == TeamMode.SOLO || !mode().teamTabEntries()) {
            return Optional.empty();
        }
        Component entry = texts().i18n().render(locale, "tab.entry.team", texts().team(locale, team),
                Placeholder.unparsed("name", name));
        return Optional.of(new TabService.Entry(entry, 1000 - team.index()));
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("Runde nicht aktiv");
        }
        return value;
    }
}
