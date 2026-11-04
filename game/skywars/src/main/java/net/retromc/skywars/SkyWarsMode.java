package net.retromc.skywars;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.games.ModeLevelStore;
import net.retromc.core.api.map.MapMeta;
import net.retromc.core.i18n.Messages;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameMode;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.end.GameResult;
import net.retromc.game.framework.kill.KillOptions;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.spectate.SpectatorService;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.game.framework.team.TeamGui;
import net.retromc.paper.core.holo.HologramService;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.stats.RoundSummary;
import net.retromc.paper.core.tab.NametagService;
import net.retromc.paper.core.title.TitleService;
import net.retromc.skywars.board.LevelTag;
import net.retromc.skywars.board.MapMenu;
import net.retromc.skywars.board.SkyWarsBoard;
import net.retromc.skywars.border.BorderService;
import net.retromc.skywars.challenge.ChallengeService;
import net.retromc.skywars.challenge.ChallengeSet;
import net.retromc.skywars.challenge.ChallengeTracker;
import net.retromc.skywars.chest.ChestService;
import net.retromc.skywars.chest.LootTables;
import net.retromc.skywars.event.BorderStartEvent;
import net.retromc.skywars.event.ChestRefillEvent;
import net.retromc.skywars.event.GlobalEventSchedule;
import net.retromc.skywars.kit.Abilities;
import net.retromc.skywars.kit.KitCatalog;
import net.retromc.skywars.kit.KitDef;
import net.retromc.skywars.kit.KitGui;
import net.retromc.skywars.kit.KitItems;
import net.retromc.skywars.kit.KitService;
import net.retromc.skywars.kit.KitSettingsGui;
import net.retromc.skywars.map.SkyWarsMap;
import net.retromc.skywars.phase.RoundPlan;
import net.retromc.skywars.setup.SkyWarsSetup;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * SkyWars (Spec 04 §6.2) auf dem Spiel-Rahmen: Inseln mit Glaskäfig, Kits mit Fähigkeiten, Truhen-Loot mit
 * Garantien, Aufbauphase/Schutzzeit, Kampf, globale Events (Chestrefill), Border, Challenges, Modus-Level, Board und
 * Rundenblock. Classic-Preset ({@code skywars_classic}): 1.8-Kampf, nur Item-Kits, ohne Events und Challenges.
 */
public final class SkyWarsMode implements GameMode {

    /** Ansagen des Lobby-Countdowns (Spec 04 §6.2.1 Nr. 2). */
    static final Set<Integer> ANNOUNCE = Set.of(60, 30, 20, 10, 5, 4, 3, 2, 1);
    /** Vorbildgröße des Map-Menüs (Spec 04 §5.11). */
    static final int MAP_OPTIONS = 7;
    /** Overkill / „Übermacht“: Sieg mit mindestens so vielen Kills (Spec 04 §6.2.6). */
    static final int OVERKILL_KILLS = 6;

    private final SkyWarsPlugin plugin;
    private final Game game;
    private final SkyWarsConfig config;
    private final GlobalEventSchedule schedule;
    private final Random random = new Random();
    private final Map<UUID, SwPlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
    private final KitService kits;
    private final ChestService chests;
    private final BorderService border;
    private final Abilities abilities;
    private final ChallengeService challenges;
    private final SkyWarsBoard board;
    private final RoundPlan plan;
    private final LevelTag levelTag;
    private final List<Block> cages = new ArrayList<>();
    private final List<Entity> lobbyEntities = new ArrayList<>();
    private HologramService.@Nullable Hologram kitHologram;
    private @Nullable SkyWarsMap map;
    private @Nullable BukkitTask clock;
    private @Nullable BukkitTask lobbyClock;
    private volatile int second;
    private @Nullable Integer twoTeamsAt;
    private boolean joinInfoSent;

    SkyWarsMode(SkyWarsPlugin plugin, Game game, SkyWarsConfig config, KitCatalog catalog, LootTables loot,
            GlobalEventSchedule schedule, ChallengeSet challengeSet) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
        this.schedule = schedule;
        this.plan = new RoundPlan(config.cageSeconds(), config.protectionSeconds(), config.borderDelay(),
                config.borderTwoTeams());
        this.levelTag = new LevelTag(config.levelColors(), config.starFrom());
        this.board = new SkyWarsBoard(this);
        this.kits = new KitService(game, catalog, config.classic(), this::kitsChanged);
        this.chests = new ChestService(loot, random, game.log());
        this.border = new BorderService(config.borderShrink(), config.borderEndRadius(), config.borderDamage());
        this.abilities = new Abilities(this);
        this.challenges = new ChallengeService(this, challengeSet, random);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(abilities, plugin);
        Bukkit.getPluginManager().registerEvents(new SkyWarsChat(this), plugin);
        SkyWarsCommands.register(this);
        SkyWarsSetup.register(this);
        lobbyClock = Bukkit.getScheduler().runTaskTimer(plugin, this::lobbySecond, 20L, 20L);
    }

    // ------------------------------------------------------------------------------------------ Zugriff

    public SkyWarsPlugin plugin() {
        return plugin;
    }

    public Game game() {
        return game;
    }

    public GameTexts texts() {
        return game.texts();
    }

    public SkyWarsConfig config() {
        return config;
    }

    public boolean classic() {
        return config.classic();
    }

    public KitService kits() {
        return kits;
    }

    public ChestService chests() {
        return chests;
    }

    public Abilities abilities() {
        return abilities;
    }

    public ChallengeService challenges() {
        return challenges;
    }

    public SkyWarsBoard board() {
        return board;
    }

    public RoundPlan plan() {
        return plan;
    }

    public GlobalEventSchedule schedule() {
        return schedule;
    }

    public SkyWarsMap map() {
        SkyWarsMap current = map;
        if (current == null) {
            throw new IllegalStateException("Map nicht geladen");
        }
        return current;
    }

    public SwPlayer sw(UUID player) {
        return players.computeIfAbsent(player, SwPlayer::new);
    }

    /** Sekunden seit dem Teleport auf die Inseln. */
    public int second() {
        return second;
    }

    /** Rundenhöchstdauer in Sekunden (Sicherheitsnetz, Bossbar-Zeitanteil). */
    public int maxSeconds() {
        return Math.max(plan.borderStart(null) + config.borderShrink(), config.maxMinutes() * 60);
    }

    /** Käfig oder Schutzzeit: kein PvP-Schaden, Kit-Fähigkeiten gesperrt (Spec 04 §6.2.1 Nr. 4). */
    public boolean protection() {
        return game.state() == GameState.INGAME && plan.phase(second) != RoundPlan.Phase.FIGHT;
    }

    /** Spieler steht noch im Käfig. */
    public boolean caged() {
        return game.state() == GameState.INGAME && plan.phase(second) == RoundPlan.Phase.CAGE;
    }

    /** Lebt in der laufenden Runde. */
    public boolean playing(Player player) {
        return game.state() == GameState.INGAME && game.alive(player.getUniqueId());
    }

    /** Im selben Team (nur Teamvarianten). */
    public boolean teammates(Player a, Player b) {
        if (!game.variant().teamPlay() || a.equals(b)) {
            return false;
        }
        GameTeam team = game.teams().teamOf(a.getUniqueId());
        return team != null && team.contains(b.getUniqueId());
    }

    /** Lebende Gegner im Umkreis. */
    public List<Player> enemies(Player player, double radius) {
        List<Player> enemies = new ArrayList<>();
        double squared = radius * radius;
        for (Player other : game.alivePlayers()) {
            if (!other.equals(player) && !teammates(player, other) && other.getWorld().equals(player.getWorld())
                    && other.getLocation().distanceSquared(player.getLocation()) <= squared) {
                enemies.add(other);
            }
        }
        return enemies;
    }

    /** Modus-Level eines Spielers (geladen beim Join, 1 ohne Daten). */
    public int level(UUID player) {
        return levels.getOrDefault(player, 1);
    }

    /** Level-Platzhalter {@code <lvl>} und {@code <lvl_color>}. */
    public TagResolver levelTag(UUID player) {
        int level = level(player);
        return TagResolver.resolver(Placeholder.unparsed("lvl", levelTag.text(level)),
                net.retromc.platform.common.text.ColorText.styling("lvl_color", levelTag.color(level)));
    }

    // ------------------------------------------------------------------------------------------ GameMode

    @Override
    public Set<Integer> countdownAnnouncements() {
        return ANNOUNCE;
    }

    @Override
    public Map<String, String> keyOverrides() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("common.join", "sw.join");
        keys.put("common.quit", "sw.quit.lobby");
        keys.put("common.online", "sw.join.online");
        keys.put("common.map", "sw.join.map");
        keys.put("common.countdown", "sw.countdown");
        keys.put("common.teaming.warn", "sw.teaming");
        keys.put("common.team.chosen", "sw.team.joined");
        keys.put("common.kill", "sw.kill");
        keys.put("common.kill.xp", "sw.kill.xp");
        keys.put("common.kill.kit", "sw.kill.kit");
        keys.put("common.death", "sw.death");
        keys.put("common.killer_health", "sw.life");
        keys.put("common.remaining", "sw.remaining");
        keys.put("common.win.player", "sw.win");
        keys.put("common.win.team", "sw.win.team");
        keys.put("common.restart", "sw.end.restart");
        keys.put("common.spectator.chat", "sw.chat.dead");
        keys.put("common.vote.done", "sw.mapvote");
        keys.put("common.vote.result", "sw.mapvote.result");
        keys.put("title.win", game.variant().teamPlay() ? "sw.title.win.team" : "sw.title.win");
        keys.put("title.win.sub", "sw.title.win.sub");
        return keys;
    }

    @Override
    public List<String> tipKeys() {
        return classic() ? List.of() : List.of("sw.tip.shield", "sw.tip.xp");
    }

    @Override
    public Material teamItem() {
        return Material.WHITE_BANNER;
    }

    @Override
    public boolean teamingForbidden() {
        return game.variant().teamSize() == 1;
    }

    @Override
    public boolean partyFollowsLeader() {
        return true;
    }

    @Override
    public boolean teamTabEntries() {
        return false;
    }

    @Override
    public int voteOptions() {
        return MAP_OPTIONS;
    }

    @Override
    public TeamGui.Style teamGuiStyle() {
        return new TeamGui.Style(true, "sw.team.gui.name", "sw.team.gui.count", "sw.team.gui.member", null,
                "sw.team.gui.join", null, "sw.team.gui.full", true, false);
    }

    @Override
    public WaitingBlock waitingBlock() {
        return new WaitingBlock() {
            @Override
            public String labelKey() {
                return "sw.board.kit";
            }

            @Override
            public Component value(Game current, Player viewer, Locale locale) {
                return board.kitValue(locale, viewer.getUniqueId());
            }
        };
    }

    /** Wartelobby-Hotbar (Spec 04 §4.11): 1 Kits, 2 Team (Teamvarianten), 3 Map-Menü, 5 Kit-Einstellungen. */
    @Override
    public void waitingHotbar(Player player, HotbarLayout layout, Locale locale) {
        layout.slot(0, SpectatorService.item(texts(), locale, Material.CHEST, "sw.hotbar.kits"),
                (clicker, right) -> KitGui.open(game, kits, clicker));
        if (game.variant().teamPlay()) {
            layout.slot(1, SpectatorService.item(texts(), locale, Material.WHITE_BANNER, "game.hotbar.team"),
                    (clicker, right) -> game.openTeamGui(clicker));
        } else {
            layout.remove(1);
        }
        layout.slot(2, SpectatorService.item(texts(), locale, Material.PAPER, "game.hotbar.map"),
                (clicker, right) -> MapMenu.open(game, clicker));
        layout.slot(4, SpectatorService.item(texts(), locale, Material.COMPARATOR, "sw.hotbar.kitsettings"),
                (clicker, right) -> KitSettingsGui.open(game, kits, clicker));
    }

    @Override
    public List<String> validateMap(GameMap gameMap) {
        return new SkyWarsMap(gameMap).validate(game.variant());
    }

    @Override
    public void onMapReady(GameMap gameMap, World world) {
        map = new SkyWarsMap(gameMap);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setDifficulty(Difficulty.NORMAL);
        spawnKitHologram();
    }

    /** Hologramm „Kit-Einstellungen“ mit Klickfläche in der Wartelobby (Spec 04 §4.9). */
    private void spawnKitHologram() {
        removeLobbyEntities();
        Optional<MapMeta.Spawn> spot = map().kitSettings();
        if (spot.isEmpty() || !game.maps().ready()) {
            return;
        }
        World world = game.maps().waitingSpawn().getWorld();
        MapMeta.Spawn spawn = spot.get();
        Location location = new Location(world, spawn.x(), spawn.y(), spawn.z());
        game.services().find(HologramService.class).ifPresent(holograms -> kitHologram = holograms.global(
                location.clone().add(0, 2.2, 0), () -> List.of(
                        texts().i18n().render(Messages.DEFAULT_LOCALE, "sw.holo.kitsettings"),
                        texts().i18n().render(Messages.DEFAULT_LOCALE, "sw.holo.kitsettings.sub"))));
        Interaction interaction = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(1.2f);
            entity.setInteractionHeight(2.2f);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        lobbyEntities.add(interaction);
    }

    /** Klickfläche des Kit-Einstellungen-Hologramms? */
    boolean kitHologram(Entity entity) {
        return lobbyEntities.contains(entity);
    }

    private void removeLobbyEntities() {
        lobbyEntities.forEach(Entity::remove);
        lobbyEntities.clear();
        HologramService.Hologram hologram = kitHologram;
        if (hologram != null) {
            hologram.remove();
        }
        kitHologram = null;
    }

    // ------------------------------------------------------------------------------------------ Wartelobby

    /** Join in die Wartelobby: Kits und Level laden. */
    void loadPlayer(Player player) {
        kits.load(player);
        UUID uuid = player.getUniqueId();
        game.services().find(ModeLevelStore.class).ifPresent(store -> game.mainThread().accept(
                store.level(uuid, game.definition().modeId()).exceptionally(error -> null), "Modus-Level",
                level -> {
                    if (level != null) {
                        levels.put(uuid, level.level());
                    }
                }));
    }

    void forgetPlayer(UUID player) {
        if (game.lifecycle().waiting()) {
            kits.forget(player);
            players.remove(player);
            levels.remove(player);
        }
    }

    private void kitsChanged() {
        if (game.lifecycle().waiting()) {
            game.waiting().refreshAll();
        }
    }

    /** 1 Hz in der Wartelobby: bei Countdown 10 s Spielerzahl und Map (Spec 04 §6.2.1 Nr. 2). */
    private void lobbySecond() {
        if (game.state() != GameState.STARTING) {
            joinInfoSent = false;
            return;
        }
        if (!joinInfoSent && game.countdownRest() <= 10) {
            joinInfoSent = true;
            int online = Bukkit.getOnlinePlayers().size();
            for (Player player : Bukkit.getOnlinePlayers()) {
                texts().send(player, "common.online", Placeholder.unparsed("n", Integer.toString(online)),
                        Placeholder.unparsed("cur", Integer.toString(online)),
                        Placeholder.unparsed("max", Integer.toString(game.maxPlayers())));
                game.sendMapLine(player);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Start

    @Override
    public void onStart() {
        SkyWarsMap current = map();
        BukkitTask lobby = lobbyClock;
        if (lobby != null) {
            lobby.cancel();
        }
        lobbyClock = null;
        removeLobbyEntities();
        second = 0;
        twoTeamsAt = null;
        World world = game.maps().world();
        chests.fillStart(world, current, game.variant().teams());
        border.prepare(world, current.borderX(), current.borderZ(), current.borderRadius());
        for (GameTeam team : game.teams().teams()) {
            Optional<SkyWarsMap.Island> island = current.island(team.index());
            List<Player> members = team.members().stream().map(Bukkit::getPlayer)
                    .filter(player -> player != null && game.alive(player.getUniqueId())).toList();
            List<MapMeta.Spawn> spawns = island.map(SkyWarsMap.Island::spawns).orElse(List.of());
            for (int i = 0; i < members.size(); i++) {
                Player player = members.get(i);
                // je Spieler ein Spawn der Insel (Spec 04 §6.2.2), sonst bleibt der Spawn des Rahmens
                cage(player, spawns.isEmpty() ? player.getLocation() : game.location(spawns.get(i % spawns.size())));
            }
        }
        for (Player player : game.alivePlayers()) {
            UUID uuid = player.getUniqueId();
            SwPlayer sw = sw(uuid);
            KitDef kit = kits.resolve(uuid);
            sw.kit(kit);
            Locale locale = texts().locale(player);
            KitItems.apply(player, kit, texts().i18n(), locale);
            texts().send(player, "sw.teleported");
            game.services().find(TitleService.class).ifPresent(titles -> titles.show(player, "sw.title.start",
                    "sw.title.start.sub", TitleService.ticks(10, 40, 10), Placeholder.unparsed("map",
                            current.map().displayName())));
            texts().send(player, "sw.kit.announce", texts().name("name", player), Placeholder.component("kit",
                    texts().i18n().render(locale, kit.nameKey())));
            decorate(player);
        }
        if (!classic()) {
            challenges.start();
        }
        announceCage(0);
        board.start();
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Glaskäfig 1 Block über dem Inselboden (Spec 04 §6.2.1 Nr. 3, AN). */
    private void cage(Player player, Location spawn) {
        Location inside = spawn.clone().add(0, 1, 0);
        player.teleport(inside);
        Block feet = inside.getBlock();
        int[][] glass = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1},
            {0, 1, -1}, {0, 2, 0}};
        for (int[] offset : glass) {
            Block block = feet.getRelative(offset[0], offset[1], offset[2]);
            if (block.getType().isAir()) {
                block.setType(Material.GLASS, false);
                cages.add(block);
            }
        }
    }

    private void openCages() {
        for (Block block : cages) {
            if (block.getType() == Material.GLASS) {
                block.setType(Material.AIR, false);
            }
        }
        cages.clear();
    }

    /** Ist ein Block Teil eines Käfigs (nicht abbaubar)? */
    public boolean cageBlock(Block block) {
        return cages.contains(block);
    }

    /** Nametag {@code §f{name} §7[{lvlColor}{level}§7]}, Teamvarianten Präfix {@code §f#{n} } (Spec 04 §4.5). */
    private void decorate(Player player) {
        UUID uuid = player.getUniqueId();
        GameTeam team = game.teams().teamOf(uuid);
        game.services().find(NametagService.class).ifPresent(nametags -> {
            if (team != null && game.variant().teamPlay()) {
                nametags.prefix(player, texts().i18n().render(Messages.DEFAULT_LOCALE, classic()
                        ? "sw.nametag.team.legacy" : "sw.nametag.team", Placeholder.unparsed("n",
                                Integer.toString(team.index() + 1))));
            } else {
                nametags.prefix(player, Component.empty());
            }
            nametags.nameColor(player, NamedTextColor.WHITE);
            boolean visible = game.services().find(net.retromc.paper.core.profile.PlayerProfiles.class)
                    .flatMap(profiles -> profiles.view(uuid)).map(view -> view.levelVisible()).orElse(true);
            nametags.suffix(player, visible ? texts().i18n().render(Messages.DEFAULT_LOCALE, "sw.nametag.level",
                    levelTag(uuid)) : Component.empty());
        });
    }

    // ------------------------------------------------------------------------------------------ Rundenuhr

    private void announceCage(int at) {
        plan.cageCountdown(at).ifPresent(rest -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                texts().send(player, texts().count("common.countdown", rest), Placeholder.unparsed("n",
                        Integer.toString(rest)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
        });
    }

    /** Rundenuhr (1 Hz): Käfig, Schutzzeit, Kampf, Events, Border, Fähigkeiten, Challenges, Board. */
    private void tick() {
        if (game.state() != GameState.INGAME) {
            return;
        }
        second++;
        announceCage(second);
        if (second == plan.protectionStart()) {
            openCages();
            texts().broadcast("sw.phase.build");
        }
        plan.protectionAnnounce(second).ifPresent(rest -> texts().broadcast(texts().count("sw.protection", rest),
                Placeholder.unparsed("n", Integer.toString(rest))));
        if (second == plan.fightStart()) {
            texts().broadcast("sw.phase.fight");
            texts().broadcast("sw.border.in", Placeholder.unparsed("n", Integer.toString(plan.borderStart(
                    twoTeamsAt) - second)));
            abilities.announceReady();
        }
        if (second >= plan.fightStart()) {
            fight();
        }
        abilities.second();
        trackChallenges();
        if (!classic()) {
            challenges.update();
        }
        if (second >= maxSeconds()) {
            game.finish(GameResult.ofDraw());
            return;
        }
        board.refresh();
    }

    private void fight() {
        int fightSecond = second - plan.fightStart();
        if (!classic()) {
            schedule.due(fightSecond).forEach(this::trigger);
        }
        if (!border.started()) {
            plan.borderAnnounce(second, twoTeamsAt).ifPresent(rest -> texts().broadcast(texts().count(
                    "sw.border.in", rest), Placeholder.unparsed("n", Integer.toString(rest))));
            if (second >= plan.borderStart(twoTeamsAt)) {
                startBorder(twoTeamsAt != null && plan.borderStart(twoTeamsAt) < plan.borderStart(null)
                        ? BorderStartEvent.Cause.TWO_TEAMS : BorderStartEvent.Cause.TIME);
            }
        }
        border.damage(game.alivePlayers());
    }

    private void startBorder(BorderStartEvent.Cause cause) {
        border.start();
        texts().broadcast("sw.border.start");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
        Bukkit.getPluginManager().callEvent(new BorderStartEvent(cause, config.borderShrink()));
    }

    /** Löst ein globales Event aus (Zeitplan oder {@code /swdebug event}). */
    public void trigger(GlobalEventSchedule.Entry entry) {
        if (!GlobalEventSchedule.CHESTREFILL.equals(entry.type()) || game.state() != GameState.INGAME) {
            return;
        }
        chests.refill(entry.stage());
        players.values().forEach(SwPlayer::resetOpened);
        texts().broadcast("sw.event", Placeholder.component("event", texts().i18n().render(Messages.DEFAULT_LOCALE,
                "sw.event." + entry.type())), Placeholder.unparsed("n", Integer.toString(entry.stage())));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        }
        Bukkit.getPluginManager().callEvent(new ChestRefillEvent(entry.stage()));
    }

    /** Challenges, die sich aus dem Zustand ergeben: Mitte betreten, Rüstung angelegt, Schwert gehalten. */
    private void trackChallenges() {
        SkyWarsMap current = map;
        for (Player player : game.alivePlayers()) {
            SwPlayer sw = sw(player.getUniqueId());
            Location at = player.getLocation();
            boolean mid = current != null && current.mid().contains(at.getX(), at.getY(), at.getZ());
            sw.inMid(mid);
            ChallengeTracker tracker = sw.challenge();
            if (tracker == null || caged()) {
                continue;
            }
            if (mid) {
                challenges.report(player, tracker.midEntered());
            }
            PlayerInventory inventory = player.getInventory();
            for (ItemStack armor : inventory.getArmorContents()) {
                if (armor != null && !armor.getType().isAir() && armor.getType() != Material.ELYTRA) {
                    challenges.report(player, tracker.armorWorn());
                    break;
                }
            }
            if (!protection() && inventory.getItemInMainHand().getType().name().endsWith("_SWORD")) {
                challenges.report(player, tracker.swordUsed());
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Tod

    /**
     * Tod (abgefangen, kein Vanilla-Tod): Kill-Feed in der Reihenfolge Kit → getötet → Leben (Opfer) →
     * verbleibende Spieler (Spec 04 §9 #13), Loot fällt, Opfer wird Zuschauer.
     */
    public void die(Player victim, boolean voidDeath) {
        UUID uuid = victim.getUniqueId();
        if (!playing(victim)) {
            return;
        }
        Player killer = game.damage().lastAttacker(uuid, Instant.now()).map(Bukkit::getPlayer)
                .filter(found -> found.isOnline() && !found.equals(victim) && game.alive(found.getUniqueId()))
                .orElse(null);
        SwPlayer sw = sw(uuid);
        sw.died(second);
        game.player(uuid).ifPresent(GamePlayer::addDeath);
        int killXp = config.rewards().kill();
        if (killer != null) {
            credit(killer, killXp);
        }
        KitDef kit = sw.kit();
        String kitName = kit == null ? null : PlainTextComponentSerializer.plainText().serialize(texts().i18n()
                .render(Messages.DEFAULT_LOCALE, kit.nameKey()));
        int remaining = Math.max(0, game.alivePlayers().size() - 1);
        game.killFeed().kill(victim, killer, killOptions(killer == null ? 0 : killXp, kitName, remaining));
        if (!voidDeath) {
            Location at = victim.getLocation();
            for (ItemStack item : victim.getInventory().getContents()) {
                if (item != null && !item.getType().isAir() && KitItems.abilityKit(item).isEmpty()) {
                    victim.getWorld().dropItemNaturally(at, item);
                }
            }
        }
        victim.getInventory().clear();
        victim.setFireTicks(0);
        victim.setFallDistance(0);
        ChallengeTracker tracker = sw.challenge();
        if (tracker != null) {
            challenges.report(victim, tracker.died(teamCanStillWin(victim)));
        }
        game.damage().clear(uuid);
        game.eliminate(victim, killer);
        checkTwoTeams();
        board.refresh();
    }

    /**
     * Bausteine der Kill-Meldung (Spec 04 §7.2): Kit des Opfers an alle ({@code sw.kill.kit}), Rundsendung mit
     * XP-Suffix nur in der Ansicht des Killers ({@code sw.kill}), Leben des Killers an das Opfer ({@code sw.life}),
     * verbleibende Spieler ({@code sw.remaining}).
     */
    static KillOptions killOptions(int xp, @Nullable String victimKit, int remaining) {
        return new KillOptions(0, xp, null, false, false, remaining, true, true, victimKit);
    }

    /** Kill gutschreiben: Zähler, XP, passive Fähigkeiten, Challenge. */
    private void credit(Player killer, int xp) {
        game.player(killer.getUniqueId()).ifPresent(gamePlayer -> {
            gamePlayer.addKill();
            gamePlayer.addXp(xp);
        });
        abilities.onKill(killer);
        ChallengeTracker tracker = sw(killer.getUniqueId()).challenge();
        if (tracker != null) {
            challenges.report(killer, tracker.kill());
        }
    }

    private boolean teamCanStillWin(Player victim) {
        GameTeam team = game.teams().teamOf(victim.getUniqueId());
        return team != null && game.variant().teamPlay() && team.members().stream()
                .anyMatch(member -> !member.equals(victim.getUniqueId()) && game.alive(member));
    }

    /** „Nur noch 2 Teams“: Border 60 s später, falls das früher ist (Spec 04 §6.2.1 Nr. 7). */
    void checkTwoTeams() {
        if (game.state() != GameState.INGAME || twoTeamsAt != null || border.started()) {
            return;
        }
        int alive = game.definition().teamMode() == TeamMode.SOLO ? game.alivePlayers().size()
                : game.teams().alive(game::alive).size();
        if (alive != 2) {
            return;
        }
        twoTeamsAt = second;
        if (plan.twoTeamsShortens(second)) {
            texts().broadcast("sw.border.twoteams", Placeholder.unparsed("n", Integer.toString(
                    plan.borderStart(twoTeamsAt) - second)));
        }
    }

    @Override
    public boolean onIngameQuit(Player player, GamePlayer gamePlayer) {
        UUID uuid = player.getUniqueId();
        Player killer = game.damage().lastAttacker(uuid, Instant.now()).map(Bukkit::getPlayer)
                .filter(found -> found.isOnline() && game.alive(found.getUniqueId())).orElse(null);
        sw(uuid).died(second);
        gamePlayer.addDeath();
        if (killer != null) {
            credit(killer, config.rewards().kill());
        }
        ChallengeTracker tracker = sw(uuid).challenge();
        if (tracker != null) {
            tracker.died(false);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            int remaining = game.alivePlayers().size();
            texts().broadcast(texts().count("common.remaining", remaining), Placeholder.unparsed("n",
                    Integer.toString(remaining)));
            checkTwoTeams();
            board.refresh();
        });
        return false;
    }

    // ------------------------------------------------------------------------------------------ Ende

    @Override
    public void onFinish(GameResult result) {
        stopTasks();
        openCages();
        abilities.clear();
        for (GamePlayer gamePlayer : game.players()) {
            if (!gamePlayer.participant()) {
                continue;
            }
            SwPlayer sw = sw(gamePlayer.uuid());
            long survived = sw.diedAtSecond() >= 0 ? sw.diedAtSecond() : second;
            gamePlayer.addXp(config.rewards().endXp(result.won(gamePlayer), survived));
        }
        if (!classic()) {
            challenges.finish(result);
        }
        if (result.aborted()) {
            return;
        }
        for (GamePlayer gamePlayer : game.players()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null && result.won(gamePlayer) && gamePlayer.kills() >= OVERKILL_KILLS) {
                game.services().find(TitleService.class).ifPresent(titles -> titles.show(player,
                        "sw.achievement.overkill", "sw.achievement.overkill.sub", TitleService.ticks(10, 70, 20)));
            }
        }
    }

    @Override
    public RoundSummary summary(GamePlayer player, GameResult result) {
        SwPlayer sw = sw(player.uuid());
        boolean won = result.won(player);
        List<RoundSummary.Line> lines = List.of(
                new RoundSummary.Line("round.label.points", Long.toString(SkyWarsRewards.points(player.kills(),
                        won))),
                new RoundSummary.Line("round.label.kills", Integer.toString(player.kills())),
                new RoundSummary.Line("sw.round.deaths", Integer.toString(player.deaths())),
                new RoundSummary.Line("sw.round.chests", Integer.toString(sw.chestsOpened())),
                new RoundSummary.Line("sw.round.blocks", Integer.toString(sw.blocksPlaced())));
        List<RoundSummary.Mark> marks = classic() || sw.challenge() == null ? List.of()
                : List.of(new RoundSummary.Mark("sw.round.challenge", challenges.done(player, result)));
        return new RoundSummary(lines, player.xp(), null, null, false, marks);
    }

    @Override
    public long baseCoins(GamePlayer player, GameResult result) {
        return config.rewards().coins(player.xp(), player.kills());
    }

    @Override
    public void onEnd(GameResult result, boolean counted) {
        if (!counted) {
            return;
        }
        game.services().find(ModeLevelStore.class).ifPresent(store -> {
            for (GamePlayer player : game.players()) {
                if (player.participant() && player.xp() > 0) {
                    store.addXp(player.uuid(), game.definition().modeId(), player.xp()).exceptionally(error -> {
                        game.log().warn("Modus-Level von {} nicht gebucht: {}", player.uuid(), error.toString());
                        return null;
                    });
                }
            }
        });
    }

    @Override
    public void onDisable() {
        stopTasks();
        BukkitTask lobby = lobbyClock;
        if (lobby != null) {
            lobby.cancel();
        }
        lobbyClock = null;
        openCages();
        abilities.clear();
        border.reset();
        removeLobbyEntities();
    }

    private void stopTasks() {
        BukkitTask task = clock;
        if (task != null) {
            task.cancel();
        }
        clock = null;
    }
}
