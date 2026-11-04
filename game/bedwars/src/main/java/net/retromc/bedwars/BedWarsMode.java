package net.retromc.bedwars;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.bedwars.board.BedWarsBoard;
import net.retromc.bedwars.event.BedDestroyEvent;
import net.retromc.bedwars.event.FinalKillEvent;
import net.retromc.bedwars.event.ResourceTransferEvent;
import net.retromc.bedwars.event.TeamEliminatedEvent;
import net.retromc.bedwars.map.BedWarsMap;
import net.retromc.bedwars.shop.ShopCatalog;
import net.retromc.bedwars.shop.ShopItems;
import net.retromc.bedwars.shop.ShopService;
import net.retromc.bedwars.spawner.GeneratorService;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.bedwars.spawner.ResourceList;
import net.retromc.core.api.games.ModeLevelStore;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameMode;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.end.GameResult;
import net.retromc.game.framework.kill.KillOptions;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapValidator;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.game.framework.team.TeamAllocator;
import net.retromc.game.framework.team.TeamColor;
import net.retromc.game.framework.team.TeamGui;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.stats.RoundSummary;
import net.retromc.paper.core.title.TitleService;
import net.retromc.game.framework.spectate.SpectatorService;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * BedWars (Spec 04 §6.1) auf dem Spiel-Rahmen: Teams mit Betten, Respawn nach 5 s solange das Bett steht, Final
 * Kills, Ressourcen-Übertragung, Team-Truhe, Generatoren, Shop, Upgrades, Fallen, Board und Rundenhöchstdauer.
 */
public final class BedWarsMode implements GameMode {

    /** Ansagen des Lobby-Countdowns (Spec 04 §6.1.2: zusätzlich 15). */
    static final Set<Integer> ANNOUNCE = Set.of(60, 50, 40, 30, 20, 15, 10, 5, 4, 3, 2, 1);

    private final BedWarsPlugin plugin;
    private final Game game;
    private final BedWarsConfig config;
    private final ShopCatalog catalog;
    private final Map<GameTeam, BwTeam> teams = new ConcurrentHashMap<>();
    private final Map<UUID, BwPlayer> players = new ConcurrentHashMap<>();
    private final Set<Long> placed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> respawning = new ConcurrentHashMap<>();
    private final ShopService shop;
    private final GeneratorService generators;
    private final BedWarsBoard board;
    private final Cooldowns cooldowns = new Cooldowns();
    private final GoldVote goldVote;
    private @Nullable BedWarsMap map;
    private @Nullable BukkitTask clock;
    private boolean suddenDeath;

    BedWarsMode(BedWarsPlugin plugin, Game game, BedWarsConfig config, ShopCatalog catalog) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
        this.catalog = catalog;
        this.shop = new ShopService(this);
        this.generators = new GeneratorService(this);
        this.board = new BedWarsBoard(this);
        this.goldVote = new GoldVote(this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new BuildListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new SpecialItems(this), plugin);
        BedWarsCommands.register(this);
    }

    // ------------------------------------------------------------------------------------------ Zugriff

    public BedWarsPlugin plugin() {
        return plugin;
    }

    public Game game() {
        return game;
    }

    public GameTexts texts() {
        return game.texts();
    }

    public BedWarsConfig config() {
        return config;
    }

    public ShopCatalog catalog() {
        return catalog;
    }

    public ShopService shop() {
        return shop;
    }

    public GeneratorService generators() {
        return generators;
    }

    public BedWarsBoard board() {
        return board;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public GoldVote goldVote() {
        return goldVote;
    }

    public BedWarsMap map() {
        BedWarsMap current = map;
        if (current == null) {
            throw new IllegalStateException("Map nicht geladen");
        }
        return current;
    }

    public boolean classic() {
        return config.classic();
    }

    public Collection<BwTeam> bwTeams() {
        return teams.values();
    }

    public Optional<BwTeam> team(GameTeam team) {
        return Optional.ofNullable(teams.get(team));
    }

    public Optional<BwTeam> teamOf(UUID player) {
        GameTeam team = game.teams().teamOf(player);
        return team == null ? Optional.empty() : team(team);
    }

    public BwPlayer bw(UUID player) {
        return players.computeIfAbsent(player, BwPlayer::new);
    }

    /** Lebt und ist nicht gerade im Respawn. */
    public boolean playing(Player player) {
        return game.state() == GameState.INGAME && game.alive(player.getUniqueId())
                && !respawning.containsKey(player.getUniqueId());
    }

    public boolean respawning(UUID player) {
        return respawning.containsKey(player);
    }

    /** Von Spielern gesetzter Block (abbaubar). */
    public boolean placed(Block block) {
        return placed.contains(key(block));
    }

    public void markPlaced(Block block, boolean value) {
        if (value) {
            placed.add(key(block));
        } else {
            placed.remove(key(block));
        }
    }

    /** Blockposition als Zahl (x, z je 27 Bit, y 10 Bit). */
    static long key(Block block) {
        return ((long) block.getX() & 0x7FFFFFFL) | (((long) block.getZ() & 0x7FFFFFFL) << 27)
                | (((long) block.getY() & 0x3FFL) << 54);
    }

    public boolean suddenDeath() {
        return suddenDeath;
    }

    // ------------------------------------------------------------------------------------------ GameMode

    @Override
    public List<TeamColor> teamOrder() {
        return TeamColor.BEDWARS_ORDER;
    }

    @Override
    public TeamAllocator.Strategy allocation() {
        return TeamAllocator.Strategy.PREFER_FILLED;
    }

    @Override
    public int allocateAt() {
        return 10;
    }

    @Override
    public Set<Integer> countdownAnnouncements() {
        return ANNOUNCE;
    }

    @Override
    public Map<String, String> keyOverrides() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("common.join", "bw.join");
        keys.put("common.quit", "bw.quit.lobby");
        keys.put("common.online", "bw.join.online");
        keys.put("common.map", "bw.join.map");
        keys.put("common.countdown", "bw.countdown");
        keys.put("common.countdown.cancel", "bw.countdown.abort");
        keys.put("common.teaming.warn", "bw.teaming");
        keys.put("common.team.chosen", "bw.team.joined");
        keys.put("common.team.full", "bw.team.full");
        keys.put("common.team.party_split", "bw.party.split");
        keys.put("common.kill", "bw.kill");
        keys.put("common.death", "bw.died");
        keys.put("common.killed_by", "bw.death.you");
        keys.put("common.kill.self", "bw.kill.you");
        keys.put("common.team.eliminated", "bw.team.eliminated");
        keys.put("common.win.team", "bw.win");
        keys.put("common.restart", "bw.end.restart");
        keys.put("common.spectator", "bw.spectator");
        keys.put("common.spectator.tp", "bw.spectate.tp");
        keys.put("common.spectator.chat", "bw.chat.dead");
        keys.put("game.chat.team", "bw.chat.team");
        keys.put("game.chat.all", "bw.chat.all");
        keys.put("title.win", "bw.title.win");
        keys.put("title.win.sub", "bw.title.win.sub");
        return keys;
    }

    @Override
    public List<String> tipKeys() {
        return List.of("bw.tip.armor", "bw.tip.copper");
    }

    @Override
    public Material teamItem() {
        return Material.RED_BED;
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
    public TeamGui.Style teamGuiStyle() {
        return new TeamGui.Style(true, "bw.team.gui.name", "bw.team.gui.count", "bw.team.gui.member", null,
                "bw.team.gui.join", null, "bw.team.gui.full", true, false);
    }

    @Override
    public void waitingHotbar(Player player, HotbarLayout layout, Locale locale) {
        layout.slot(4, SpectatorService.item(texts(), locale, Material.RED_BUNDLE, "bw.hotbar.shopsettings"),
                (clicker, right) -> ShopSettingsGui.open(this, clicker));
        if (classic()) {
            layout.slot(3, SpectatorService.item(texts(), locale, Material.GOLD_INGOT, "bw.hotbar.goldvote"),
                    (clicker, right) -> goldVote.open(clicker));
        }
    }

    @Override
    public List<String> validateMap(GameMap gameMap) {
        return new BedWarsMap(gameMap).validate(MapValidator.teamIds(gameMap, game.variant(), teamOrder()),
                classic());
    }

    @Override
    public void onMapReady(GameMap gameMap, World world) {
        map = new BedWarsMap(gameMap);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
    }

    // ------------------------------------------------------------------------------------------ Start

    @Override
    public void onStart() {
        BedWarsMap bwMap = map();
        goldVote.finish();
        for (GameTeam team : game.teams().teams()) {
            Location spawn = bwMap.map().teamSpawn(team.id()).map(game::location)
                    .orElseGet(() -> game.maps().spectatorSpawn());
            Location head = bwMap.bed(team.id()).map(bed -> game.location(bed.head())).orElse(null);
            Location foot = bwMap.bed(team.id()).map(bed -> game.location(bed.foot())).orElse(null);
            Location chest = bwMap.chest(team.id()).map(game::location).orElse(null);
            Component title = texts().i18n().render(net.retromc.core.i18n.Messages.DEFAULT_LOCALE,
                    "bw.chest.title", texts().team(net.retromc.core.i18n.Messages.DEFAULT_LOCALE, team));
            teams.put(team, new BwTeam(team, spawn, head, foot, chest, title));
        }
        for (Player player : game.alivePlayers()) {
            bw(player.getUniqueId());
            shop.giveKit(player);
            texts().send(player, "bw.start");
        }
        NpcSpawner.spawn(this);
        generators.start(teams.values());
        board.start();
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    /** Rundenuhr (1 Hz): Generatorstufen, Board, Basis-Effekte, Fallen, Rundenhöchstdauer. */
    private void second() {
        if (game.state() != GameState.INGAME) {
            return;
        }
        Duration elapsed = game.elapsed();
        generators.second(elapsed);
        TrapService.check(this);
        TrapService.baseEffects(this);
        long minutes = elapsed.toSeconds();
        if (!suddenDeath && minutes >= config.maxMinutes() * 60L) {
            suddenDeath = true;
            for (BwTeam team : teams.values()) {
                breakBed(team);
            }
            texts().broadcast("bw.bed.suddendeath");
        }
        if (minutes >= (config.maxMinutes() + config.drawMinutes()) * 60L) {
            game.finish(GameResult.ofDraw());
            return;
        }
        board.refresh();
    }

    // ------------------------------------------------------------------------------------------ Bett

    /** Abbau eines Betts durch {@code destroyer} (Spec 04 §6.1.4 Nr. 1–2). */
    public boolean destroyBed(Player destroyer, BwTeam victim, Location location) {
        BedDestroyEvent event = new BedDestroyEvent(victim.team(), destroyer, location);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        breakBed(victim);
        UUID uuid = destroyer.getUniqueId();
        bw(uuid).bed();
        game.player(uuid).ifPresent(gamePlayer -> {
            gamePlayer.addExtra("beds", 1);
            gamePlayer.addXp(config.rewards().bed());
        });
        GameTeam destroyerTeam = game.teams().teamOf(uuid);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Locale locale = texts().locale(player);
            TagResolver team = texts().team(locale, victim.team());
            if (player.equals(destroyer)) {
                texts().send(player, "bw.bed.destroyed.you", team,
                        Placeholder.unparsed("xp", Integer.toString(config.rewards().bed())));
            } else if (victim.team().contains(player.getUniqueId())) {
                texts().send(player, "bw.bed.destroyed.self");
                titles(player, "bw.title.bed", "bw.title.bed.sub", TitleService.ticks(10, 50, 10));
            } else {
                texts().send(player, "bw.bed.destroyed", team, texts().name("name", destroyer));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
        if (destroyerTeam != null) {
            destroyer.playSound(destroyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        board.refresh();
        return true;
    }

    /** Entfernt das Bett ohne Meldung (Sudden Death, Abbau). */
    void breakBed(BwTeam team) {
        team.destroyBed();
        for (Location location : new Location[] {team.bedHead(), team.bedFoot()}) {
            if (location != null) {
                location.getBlock().setType(Material.AIR, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Tod

    /**
     * Tödlicher Schaden (abgefangen, kein Vanilla-Tod): Kill-Feed, Ressourcen an den Killer, Respawn nach 5 s oder
     * Final Kill (Spec 04 §6.1.4 Nr. 3–5).
     */
    public void die(Player victim, boolean voidDeath) {
        UUID uuid = victim.getUniqueId();
        if (!playing(victim)) {
            return;
        }
        Player killer = game.damage().lastAttacker(uuid, Instant.now()).map(Bukkit::getPlayer)
                .filter(Player::isOnline).orElse(null);
        BwTeam team = teamOf(uuid).orElse(null);
        boolean finalKill = team == null || !team.bedAlive();
        rememberSlots(victim);
        Map<Resource, Integer> loot = takeResources(victim);
        if (killer != null && game.alive(killer.getUniqueId())) {
            give(killer, uuid, loot, ResourceTransferEvent.Cause.KILL);
        }
        SpecialItems.explodeVest(this, victim);
        BwPlayer killerBw = killer == null ? null : bw(killer.getUniqueId());
        int killXp = classic() ? 0 : config.rewards().kill();
        if (killer != null) {
            killerBw.kill(finalKill);
            game.player(killer.getUniqueId()).ifPresent(gamePlayer -> {
                gamePlayer.addXp(killXp);
                if (finalKill) {
                    gamePlayer.addKill();
                }
            });
        }
        KillOptions options = new KillOptions(classic() && finalKill ? 5 : 0, killXp, null, false, true, -1,
                false);
        game.killFeed().kill(victim, killer, options);
        game.damage().clear(uuid);
        victim.getInventory().clear();
        victim.setFireTicks(0);
        victim.setFallDistance(0);
        if (finalKill) {
            finalDeath(victim, killer, team);
        } else {
            respawn(victim, voidDeath);
        }
    }

    private void finalDeath(Player victim, @Nullable Player killer, @Nullable BwTeam team) {
        UUID uuid = victim.getUniqueId();
        bw(uuid).finalDeath();
        game.player(uuid).ifPresent(GamePlayer::addDeath);
        if (team != null) {
            Bukkit.getPluginManager().callEvent(new FinalKillEvent(victim, killer, team.team()));
        }
        game.eliminate(victim, killer);
        if (team != null) {
            teamCheck(team, killer);
        }
        board.refresh();
    }

    /** Meldet „Team hat noch N Spieler“ bzw. „vernichtet“ und gibt die Team-Truhe an den Killer. */
    void teamCheck(BwTeam team, @Nullable Player killer) {
        int remaining = (int) team.team().members().stream().filter(game::alive).count();
        if (remaining > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                texts().send(player, "bw.team.remaining", texts().team(texts().locale(player), team.team()),
                        Placeholder.unparsed("n", Integer.toString(remaining)));
            }
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            texts().send(player, "common.team.eliminated", texts().team(texts().locale(player), team.team()));
        }
        Bukkit.getPluginManager().callEvent(new TeamEliminatedEvent(team.team(), killer));
        if (killer != null && game.alive(killer.getUniqueId())) {
            List<ItemStack> content = new ArrayList<>();
            for (ItemStack item : team.chest().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    content.add(item);
                }
            }
            team.chest().clear();
            if (!content.isEmpty()) {
                killer.getInventory().addItem(content.toArray(ItemStack[]::new)).values()
                        .forEach(rest -> killer.getWorld().dropItem(killer.getLocation(), rest));
                texts().send(killer, "bw.chest.loot", texts().team(texts().locale(killer), team.team()));
            }
        }
    }

    private void respawn(Player victim, boolean voidDeath) {
        UUID uuid = victim.getUniqueId();
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        if (voidDeath) {
            victim.teleport(game.maps().spectatorSpawn());
        }
        int seconds = config.respawnSeconds();
        texts().send(victim, texts().count("bw.respawn.in", seconds), Placeholder.unparsed("n",
                Integer.toString(seconds)));
        int[] left = {seconds};
        respawnTitle(victim, seconds);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            left[0]--;
            if (!victim.isOnline() || game.state() != GameState.INGAME) {
                cancelRespawn(uuid);
                return;
            }
            if (left[0] > 0) {
                respawnTitle(victim, left[0]);
                return;
            }
            cancelRespawn(uuid);
            BwTeam team = teamOf(uuid).orElse(null);
            game.prepare(victim);
            victim.teleport(team != null ? team.spawn() : game.maps().spectatorSpawn());
            shop.giveKit(victim);
            game.decorate(victim);
        }, 20L, 20L);
        respawning.put(uuid, task);
    }

    private void respawnTitle(Player player, int seconds) {
        titles(player, texts().count("bw.title.respawn", seconds), null, TitleService.RESPAWN,
                Placeholder.unparsed("n", Integer.toString(seconds)));
    }

    private void cancelRespawn(UUID player) {
        BukkitTask task = respawning.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    /** Merkt die Hotbar-Slots von Schwert und Spitzhacke (bleiben beim Respawn, Spec 04 §6.1.4 Nr. 3). */
    private void rememberSlots(Player player) {
        PlayerInventory inventory = player.getInventory();
        int sword = -1;
        int pickaxe = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) {
                continue;
            }
            String name = item.getType().name();
            if (sword < 0 && name.endsWith("_SWORD")) {
                sword = slot;
            } else if (pickaxe < 0 && name.endsWith("_PICKAXE")) {
                pickaxe = slot;
            }
        }
        bw(player.getUniqueId()).slots(sword, pickaxe);
    }

    // ------------------------------------------------------------------------------------------ Ressourcen

    /** Entfernt alle Ressourcen aus dem Inventar und liefert die Mengen. */
    public Map<Resource, Integer> takeResources(Player player) {
        Map<Resource, Integer> amounts = new EnumMap<>(Resource.class);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) {
                continue;
            }
            Optional<Resource> resource = Resource.byMaterial(item.getType().name());
            if (resource.isPresent()) {
                amounts.merge(resource.get(), item.getAmount(), Integer::sum);
                inventory.setItem(slot, null);
            }
        }
        return amounts;
    }

    /** Gibt Ressourcen und meldet {@code bw.resources.transfer} (nur Werte &gt; 0). */
    public void give(Player receiver, UUID from, Map<Resource, Integer> amounts, ResourceTransferEvent.Cause cause) {
        List<ResourceList.Entry> entries = ResourceList.entries(amounts);
        if (entries.isEmpty()) {
            return;
        }
        Locale locale = texts().locale(receiver);
        for (ResourceList.Entry entry : entries) {
            int left = entry.amount();
            while (left > 0) {
                int stack = Math.min(64, left);
                left -= stack;
                receiver.getInventory().addItem(ShopItems.resource(entry.resource(), stack, texts().i18n(), locale))
                        .values().forEach(rest -> receiver.getWorld().dropItem(receiver.getLocation(), rest));
            }
        }
        Component list = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                list = list.append(texts().i18n().render(locale, "bw.resources.separator"));
            }
            ResourceList.Entry entry = entries.get(i);
            list = list.append(texts().i18n().render(locale, entry.key(), Placeholder.unparsed("n",
                    Integer.toString(entry.amount()))));
        }
        texts().send(receiver, "bw.resources.transfer", Placeholder.unparsed("n", ResourceList.verbSuffix(entries)),
                Placeholder.component("list", list));
        Bukkit.getPluginManager().callEvent(new ResourceTransferEvent(from, receiver.getUniqueId(), amounts, cause));
    }

    // ------------------------------------------------------------------------------------------ Verlassen

    @Override
    public boolean onIngameQuit(Player player, GamePlayer gamePlayer) {
        UUID uuid = player.getUniqueId();
        cancelRespawn(uuid);
        BwTeam team = teamOf(uuid).orElse(null);
        Map<Resource, Integer> loot = takeResources(player);
        Player killer = game.damage().lastAttacker(uuid, Instant.now()).map(Bukkit::getPlayer)
                .filter(found -> found.isOnline() && game.alive(found.getUniqueId())).orElse(null);
        bw(uuid).finalDeath();
        gamePlayer.addDeath();
        boolean last = team != null && team.team().members().stream()
                .noneMatch(member -> !member.equals(uuid) && game.alive(member));
        List<ItemStack> chest = new ArrayList<>();
        if (last) {
            for (ItemStack item : team.chest().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    chest.add(item);
                }
            }
            team.chest().clear();
        }
        if (killer != null) {
            bw(killer.getUniqueId()).kill(true);
            game.player(killer.getUniqueId()).ifPresent(GamePlayer::addKill);
            give(killer, uuid, loot, ResourceTransferEvent.Cause.LEAVE);
            chest.forEach(item -> killer.getInventory().addItem(item).values()
                    .forEach(rest -> killer.getWorld().dropItem(killer.getLocation(), rest)));
        } else {
            List<Player> enemies = game.alivePlayers().stream().filter(other -> !other.equals(player)
                    && (team == null || !team.team().contains(other.getUniqueId()))).toList();
            if (!enemies.isEmpty()) {
                List<Map<Resource, Integer>> shares = new ArrayList<>();
                enemies.forEach(enemy -> shares.add(new EnumMap<>(Resource.class)));
                loot.forEach((resource, amount) -> {
                    List<Integer> parts = ResourceList.split(amount, enemies.size());
                    for (int i = 0; i < enemies.size(); i++) {
                        shares.get(i).merge(resource, parts.get(i), Integer::sum);
                    }
                });
                for (int i = 0; i < enemies.size(); i++) {
                    give(enemies.get(i), uuid, shares.get(i), ResourceTransferEvent.Cause.LEAVE);
                }
            }
        }
        texts().broadcast("bw.leave.ingame", texts().name("name", player));
        if (last) {
            Bukkit.getScheduler().runTask(plugin, () -> teamCheck(team, killer));
        }
        Bukkit.getScheduler().runTask(plugin, board::refresh);
        return true;
    }

    // ------------------------------------------------------------------------------------------ Ende

    @Override
    public void onFinish(GameResult result) {
        stopTasks();
        long playSeconds = game.elapsed().toSeconds();
        for (GamePlayer gamePlayer : game.players()) {
            if (!gamePlayer.participant()) {
                continue;
            }
            int xp = config.rewards().endXp(result.won(gamePlayer), gamePlayer.alive(), playSeconds);
            gamePlayer.addXp(classic() ? 0 : xp);
        }
    }

    @Override
    public RoundSummary summary(GamePlayer player, GameResult result) {
        BwPlayer bw = bw(player.uuid());
        boolean won = result.won(player);
        long points = BedWarsRewards.points(bw.finalKills(), bw.beds(), won);
        List<RoundSummary.Line> lines = List.of(
                new RoundSummary.Line("round.label.points", Long.toString(classic() ? (won ? 1 : 0) : points)),
                new RoundSummary.Line("round.label.kills", Integer.toString(bw.finalKills())),
                new RoundSummary.Line("round.label.beds", Integer.toString(bw.beds())));
        return new RoundSummary(lines, classic() ? null : player.xp(), !bw.hasFinalDeath(), won, true);
    }

    @Override
    public long baseCoins(GamePlayer player, GameResult result) {
        return config.rewards().coins(player.xp());
    }

    @Override
    public void onEnd(GameResult result, boolean counted) {
        if (!counted) {
            return;
        }
        game.services().find(ModeLevelStore.class).ifPresent(levels -> {
            for (GamePlayer player : game.players()) {
                if (player.participant() && player.xp() > 0) {
                    levels.addXp(player.uuid(), game.definition().modeId(), player.xp()).exceptionally(error -> {
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
        NpcSpawner.remove();
    }

    private void stopTasks() {
        BukkitTask task = clock;
        if (task != null) {
            task.cancel();
        }
        clock = null;
        generators.stop();
        respawning.values().forEach(BukkitTask::cancel);
        respawning.clear();
    }

    // ------------------------------------------------------------------------------------------ Hilfen

    /** Title mit Text-Schlüsseln (Ersatzschlüssel beachtet). */
    public void titles(Player player, String key, @Nullable String subKey, net.kyori.adventure.title.Title.Times times,
            TagResolver... args) {
        game.services().find(TitleService.class).ifPresent(titles -> titles.show(player, key, subKey, times, args));
    }

    /** Team-Mitglieder online. */
    public List<Player> online(BwTeam team) {
        List<Player> online = new ArrayList<>();
        team.team().members().forEach(member -> {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                online.add(player);
            }
        });
        return online;
    }

    /** Zählt Ressourcen einer Art im Inventar. */
    public static int count(Player player, Resource resource) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType().name().equals(resource.material())) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Kopie der Bestände je Spieler (Debug/Tests). */
    Map<UUID, BwPlayer> players() {
        return new HashMap<>(players);
    }
}
