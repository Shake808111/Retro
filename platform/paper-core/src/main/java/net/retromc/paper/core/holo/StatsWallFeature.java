package net.retromc.paper.core.holo;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.stats.AnchorType;
import net.retromc.core.api.stats.DisplayAnchor;
import net.retromc.core.api.stats.DisplayAnchorStore;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.core.stats.TieRotation;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.event.LocaleChangedEvent;
import net.retromc.paper.core.event.ProfileLoadedEvent;
import net.retromc.paper.core.lang.Texts;
import net.retromc.paper.core.stats.ModeCatalog;
import net.retromc.paper.core.stats.StatsService;
import net.retromc.paper.core.stats.StatsTexts;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;

/**
 * Bestenlisten-Wände und Statistik-Hologramme an den Ankern der Servergruppe ({@code core.display_anchor}, Spec 02
 * §3 {@code /statswall}, §4.9): {@code SIGN_WALL} (Schild mit Spielerkopf, Rechtsklick → {@code /stats}),
 * {@code HOLO_TOP} (für alle) und {@code HOLO_PERSONAL} (je Betrachter). Gleichstand wechselt alle 5 s; neu
 * geladen bei {@code STATS_UPDATED}/{@code LEADERBOARD_CHANGED} und spätestens nach 30 s (Top-Cache). Versteckte
 * Spieler stehen mit Namen auf Wänden und Hologrammen.
 */
public final class StatsWallFeature implements CoreFeature<PaperCore>, Listener {

    /** Recht für {@code /statswall}. */
    public static final String PERMISSION = BuildInfo.BRAND_ID + ".stats.wall";
    /** Höchster Abstand für {@code /statswall remove} und Blickposition. */
    static final int REACH = 5;
    private static final long ROTATE_TICKS = TieRotation.PERIOD.toSeconds() * 20;
    private static final long RELOAD_TICKS = 600;

    private final Map<Long, DisplayAnchor> anchors = new ConcurrentHashMap<>();
    private final Map<String, List<TopEntry>> tops = new ConcurrentHashMap<>();
    private final Map<Long, HologramService.Hologram> globals = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, HologramService.Hologram>> personals = new ConcurrentHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private @Nullable PaperCore core;
    private @Nullable Texts texts;

    @Override
    public String name() {
        return "Bestenlisten";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        texts = new Texts(context.services().supplier(I18n.class));
        registerCommands(context.services().get(CommandService.class).manager());
        MessageBus bus = context.services().get(MessageBus.class);
        registrations.add(bus.subscribe(MessageType.STATS_UPDATED, envelope -> context.mainThread().execute(() -> {
            reloadTops(envelope.d().mode());
            refreshPersonal(envelope.d().mode(), envelope.d().uuids());
        })).join());
        registrations.add(bus.subscribe(MessageType.LEADERBOARD_CHANGED, envelope -> context.mainThread()
                .execute(() -> reloadTops(envelope.d().mode()))).join());
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        tasks.add(Bukkit.getScheduler().runTaskTimer(context.plugin(), this::rotate, ROTATE_TICKS, ROTATE_TICKS));
        tasks.add(Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> reloadTops(null), RELOAD_TICKS,
                RELOAD_TICKS));
        context.mainThread().accept(context.services().get(DisplayAnchorStore.class).list(group(context)),
                "Anzeige-Anker laden", loaded -> {
                    loaded.forEach(anchor -> anchors.put(anchor.id(), anchor));
                    anchors.values().forEach(this::spawn);
                    reloadTops(null);
                });
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
        globals.values().forEach(HologramService.Hologram::remove);
        globals.clear();
        personals.values().forEach(map -> map.values().forEach(HologramService.Hologram::remove));
        personals.clear();
        anchors.clear();
        tops.clear();
        texts = null;
        core = null;
    }

    private static String group(PaperCore context) {
        return context.config().template().value();
    }

    private PaperCore context() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Bestenlisten nicht aktiv");
        }
        return context;
    }

    private Texts texts() {
        Texts current = texts;
        if (current == null) {
            throw new IllegalStateException("Bestenlisten nicht aktiv");
        }
        return current;
    }

    private Optional<StatsMode> mode(ModeKey key) {
        return context().services().find(ModeCatalog.class).flatMap(catalog -> catalog.mode(key));
    }

    private static String topKey(ModeKey mode, StatPeriod period) {
        return mode.key() + ":" + period.key();
    }

    // ------------------------------------------------------------------------------------------ Anzeige

    private void spawn(DisplayAnchor anchor) {
        World world = Bukkit.getWorld(anchor.world());
        if (world == null) {
            context().log().warn("Welt {} des Ankers #{} fehlt", anchor.world(), anchor.id());
            return;
        }
        switch (anchor.type()) {
            case HOLO_TOP -> globals.put(anchor.id(), context().services().get(HologramService.class).global(
                    location(world, anchor), () -> topLines(anchor)));
            case HOLO_PERSONAL -> Bukkit.getOnlinePlayers().forEach(player -> personal(player, anchor));
            case SIGN_WALL -> drawWall(world, anchor);
        }
    }

    private static Location location(World world, DisplayAnchor anchor) {
        return new Location(world, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0f);
    }

    private List<TopEntry> rotated(DisplayAnchor anchor) {
        List<TopEntry> entries = tops.getOrDefault(topKey(anchor.mode(), anchor.period()), List.of());
        return TieRotation.rotate(entries, Instant.now().getEpochSecond() / TieRotation.PERIOD.toSeconds(),
                anchor.size());
    }

    private List<Component> topLines(DisplayAnchor anchor) {
        Optional<StatsMode> mode = mode(anchor.mode());
        if (mode.isEmpty()) {
            return List.of();
        }
        return StatsTexts.topHologram(texts().i18n(), Messages.DEFAULT_LOCALE, mode.get(), anchor.period(),
                rotated(anchor));
    }

    /** Schilder und Köpfe der Wand; leere Plätze bleiben leer. */
    private void drawWall(World world, DisplayAnchor anchor) {
        Optional<StatsMode> mode = mode(anchor.mode());
        String facing = anchor.facing() == null ? "NORTH" : anchor.facing();
        BlockFace face = BlockFace.valueOf(facing);
        List<TopEntry> entries = rotated(anchor);
        List<WallLayout.Offset> signs = WallLayout.signs(facing, anchor.size());
        for (int i = 0; i < signs.size(); i++) {
            WallLayout.Offset offset = signs.get(i);
            Block block = world.getBlockAt((int) Math.floor(anchor.x()) + offset.dx(),
                    (int) Math.floor(anchor.y()) + offset.dy(), (int) Math.floor(anchor.z()) + offset.dz());
            if (block.getType() != Material.OAK_WALL_SIGN) {
                block.setType(Material.OAK_WALL_SIGN, false);
            }
            if (block.getBlockData() instanceof Directional directional && directional.getFacing() != face) {
                directional.setFacing(face);
                block.setBlockData(directional, false);
            }
            TopEntry entry = i < entries.size() ? entries.get(i) : null;
            if (block.getState() instanceof Sign sign) {
                SignSide front = sign.getSide(Side.FRONT);
                List<Component> lines = entry == null || mode.isEmpty() ? List.of()
                        : StatsTexts.sign(texts().i18n(), Messages.DEFAULT_LOCALE, mode.get(), entry);
                for (int line = 0; line < 4; line++) {
                    front.line(line, line < lines.size() ? lines.get(line) : Component.empty());
                }
                sign.setWaxed(true);
                sign.update(true, false);
            }
            Block head = block.getRelative(BlockFace.UP);
            if (head.getType() != Material.PLAYER_HEAD) {
                head.setType(Material.PLAYER_HEAD, false);
            }
            if (head.getBlockData() instanceof Rotatable rotatable && rotatable.getRotation() != face) {
                rotatable.setRotation(face);
                head.setBlockData(rotatable, false);
            }
            if (head.getState() instanceof Skull skull) {
                UUID owner = entry == null ? null : entry.uuid();
                ResolvableProfile shown = skull.getProfile();
                UUID current = shown == null ? null : shown.uuid();
                if (entry == null ? current != null : !entry.uuid().equals(current)) {
                    skull.setProfile(entry == null ? null : ResolvableProfile.resolvableProfile().uuid(owner)
                            .name(entry.name()).build());
                    skull.update(true, false);
                }
            }
        }
    }

    private void personal(Player player, DisplayAnchor anchor) {
        World world = Bukkit.getWorld(anchor.world());
        Optional<StatsMode> mode = mode(anchor.mode());
        if (world == null || mode.isEmpty() || !player.getWorld().equals(world)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        context().mainThread().accept(context().services().get(StatsService.class).query(uuid, anchor.mode(),
                StatWindow.of(anchor.period())), "Persönliches Hologramm", snapshot -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    I18n i18n = texts().i18n();
                    List<Component> lines = StatsTexts.personalHologram(i18n, i18n.locale(uuid), mode.get(),
                            anchor.period(), snapshot.report());
                    Map<Long, HologramService.Hologram> own = personals.computeIfAbsent(uuid,
                            key -> new ConcurrentHashMap<>());
                    HologramService.Hologram shown = own.get(anchor.id());
                    if (shown == null) {
                        own.put(anchor.id(), context().services().get(HologramService.class).personal(player,
                                location(world, anchor), lines));
                    } else {
                        shown.update(lines);
                    }
                });
    }

    private void refreshPersonal(ModeKey mode, List<UUID> players) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                anchors.values().stream().filter(anchor -> anchor.type() == AnchorType.HOLO_PERSONAL
                        && anchor.mode().equals(mode)).forEach(anchor -> personal(player, anchor));
            }
        }
    }

    /** Lädt die Top-Listen (alle oder eines Modus) und zeichnet danach neu. */
    private void reloadTops(@Nullable ModeKey only) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        anchors.values().stream().filter(anchor -> anchor.type() != AnchorType.HOLO_PERSONAL)
                .filter(anchor -> only == null || anchor.mode().equals(only))
                .map(anchor -> Map.entry(anchor.mode(), anchor.period())).distinct().forEach(key ->
                        context.mainThread().accept(context.services().get(StatsService.class).top(key.getKey(),
                                key.getValue(), 10), "Bestenliste laden", entries -> {
                                    tops.put(topKey(key.getKey(), key.getValue()), entries);
                                    rotate();
                                }));
    }

    /** Gleichstand im 5-s-Takt und neue Werte. */
    private void rotate() {
        for (DisplayAnchor anchor : anchors.values()) {
            World world = Bukkit.getWorld(anchor.world());
            if (world == null) {
                continue;
            }
            if (anchor.type() == AnchorType.HOLO_TOP) {
                HologramService.Hologram shown = globals.get(anchor.id());
                if (shown != null) {
                    shown.refresh();
                }
            } else if (anchor.type() == AnchorType.SIGN_WALL) {
                drawWall(world, anchor);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Ereignisse

    @EventHandler
    public void onLoaded(ProfileLoadedEvent event) {
        showPersonal(event.player());
    }

    @EventHandler
    public void onLocale(LocaleChangedEvent event) {
        showPersonal(event.player());
    }

    private void showPersonal(Player player) {
        anchors.values().stream().filter(anchor -> anchor.type() == AnchorType.HOLO_PERSONAL)
                .forEach(anchor -> personal(player, anchor));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Map<Long, HologramService.Hologram> own = personals.remove(event.getPlayer().getUniqueId());
        if (own != null) {
            own.values().forEach(HologramService.Hologram::remove);
        }
    }

    /** Rechtsklick auf ein Schild der Wand zeigt die Stats des Spielers (30 Tage bzw. insgesamt). */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || block.getType() != Material.OAK_WALL_SIGN) {
            return;
        }
        for (DisplayAnchor anchor : anchors.values()) {
            if (anchor.type() != AnchorType.SIGN_WALL || !anchor.world().equals(block.getWorld().getName())) {
                continue;
            }
            List<WallLayout.Offset> signs = WallLayout.signs(anchor.facing() == null ? "NORTH" : anchor.facing(),
                    anchor.size());
            List<TopEntry> entries = rotated(anchor);
            for (int i = 0; i < signs.size(); i++) {
                WallLayout.Offset offset = signs.get(i);
                if (block.getX() == (int) Math.floor(anchor.x()) + offset.dx()
                        && block.getY() == (int) Math.floor(anchor.y()) + offset.dy()
                        && block.getZ() == (int) Math.floor(anchor.z()) + offset.dz()) {
                    event.setCancelled(true);
                    if (i < entries.size()) {
                        context().services().get(StatsService.class).sendStats(event.getPlayer(), anchor.mode(),
                                StatWindow.of(anchor.period()), entries.get(i).name());
                    }
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ /statswall

    private void registerCommands(PaperCommandManager<Source> manager) {
        manager.command(manager.commandBuilder("statswall").literal("remove").permission(PERMISSION)
                .senderType(PlayerSource.class).handler(ctx -> remove(ctx.sender().source())));
        for (String type : List.of("sign", "holo", "personal")) {
            manager.command(manager.commandBuilder("statswall").literal(type).permission(PERMISSION)
                    .senderType(PlayerSource.class).required("mode", stringParser())
                    .required("period", stringParser())
                    .optional("size", integerParser(1, DisplayAnchor.DEFAULT_SIZE))
                    .handler(ctx -> create(ctx.sender().source(), type, ctx.get("mode"), ctx.get("period"),
                            ctx.getOrDefault("size", DisplayAnchor.DEFAULT_SIZE))));
        }
    }

    private void create(Player player, String type, String modeInput, String periodInput, int size) {
        Optional<StatsMode> mode = context().services().find(ModeCatalog.class).flatMap(c -> c.find(modeInput));
        if (mode.isEmpty()) {
            texts().send(player, "stats.unknown-mode", Placeholder.unparsed("mode", modeInput));
            return;
        }
        Optional<StatPeriod> period = StatPeriod.byKey(periodInput.toLowerCase(Locale.ROOT));
        if (period.isEmpty()) {
            texts().send(player, "error.command.argument", Placeholder.unparsed("input", periodInput));
            return;
        }
        Block target = player.getTargetBlockExact(REACH);
        BlockFace face = player.getTargetBlockFace(REACH);
        AnchorType anchorType = switch (type) {
            case "sign" -> AnchorType.SIGN_WALL;
            case "holo" -> AnchorType.HOLO_TOP;
            default -> AnchorType.HOLO_PERSONAL;
        };
        Location at;
        String facing = null;
        if (anchorType == AnchorType.SIGN_WALL) {
            if (target == null || face == null || !List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
                    BlockFace.WEST).contains(face)) {
                texts().send(player, "statswall.none");
                return;
            }
            at = target.getRelative(face).getLocation();
            facing = face.name();
        } else {
            at = target == null ? player.getLocation().add(0, 2, 0) : target.getLocation().add(0.5, 2.0, 0.5);
        }
        DisplayAnchor anchor = new DisplayAnchor(0, group(context()), at.getWorld().getName(), anchorType,
                mode.get().key(), period.get(), size, at.getX(), at.getY(), at.getZ(), player.getLocation().getYaw(),
                facing, player.getUniqueId());
        context().mainThread().accept(context().services().get(DisplayAnchorStore.class).create(anchor),
                "/statswall", created -> {
                    anchors.put(created.id(), created);
                    spawn(created);
                    reloadTops(created.mode());
                    texts().send(player, "statswall.created", Placeholder.unparsed("id",
                            Long.toString(created.id())));
                });
    }

    private void remove(Player player) {
        Block target = player.getTargetBlockExact(REACH);
        Location point = target == null ? player.getLocation() : target.getLocation().add(0.5, 0.5, 0.5);
        Optional<DisplayAnchor> nearest = anchors.values().stream()
                .filter(anchor -> anchor.world().equals(point.getWorld().getName()))
                .filter(anchor -> anchor.distanceSquared(point.getX(), point.getY(), point.getZ()) <= REACH * REACH)
                .min((a, b) -> Double.compare(a.distanceSquared(point.getX(), point.getY(), point.getZ()),
                        b.distanceSquared(point.getX(), point.getY(), point.getZ())));
        if (nearest.isEmpty()) {
            texts().send(player, "statswall.none");
            return;
        }
        DisplayAnchor anchor = nearest.get();
        context().mainThread().accept(context().services().get(DisplayAnchorStore.class).delete(anchor.id()),
                "/statswall remove", removed -> {
                    anchors.remove(anchor.id());
                    despawn(anchor);
                    texts().send(player, "statswall.removed");
                });
    }

    private void despawn(DisplayAnchor anchor) {
        HologramService.Hologram global = globals.remove(anchor.id());
        if (global != null) {
            global.remove();
        }
        personals.values().forEach(own -> Optional.ofNullable(own.remove(anchor.id()))
                .ifPresent(HologramService.Hologram::remove));
        World world = Bukkit.getWorld(anchor.world());
        if (anchor.type() == AnchorType.SIGN_WALL && world != null && anchor.facing() != null) {
            for (WallLayout.Offset offset : WallLayout.signs(anchor.facing(), anchor.size())) {
                Block sign = world.getBlockAt((int) Math.floor(anchor.x()) + offset.dx(),
                        (int) Math.floor(anchor.y()) + offset.dy(), (int) Math.floor(anchor.z()) + offset.dz());
                sign.getRelative(BlockFace.UP).setType(Material.AIR, false);
                sign.setType(Material.AIR, false);
            }
        }
    }
}
