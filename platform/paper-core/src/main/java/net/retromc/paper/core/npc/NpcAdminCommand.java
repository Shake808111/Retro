package net.retromc.paper.core.npc;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.npc.NpcStore;
import net.retromc.core.api.npc.NpcStore.StoredNpc;
import net.retromc.paper.core.lang.Texts;
import net.retromc.platform.common.concurrent.MainThreadExecutor;
import net.retromc.platform.common.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Admin-Befehl {@code /npc} für den {@link NpcService} (Spec 02 §8.1): legt Spieler-NPCs an der eigenen Position an,
 * listet und entfernt sie. Die NPCs sind dauerhaft: {@link NpcStore} ({@code core.npc}) speichert Ort, Name und Skin
 * (signierte {@code textures}-Property) je Servergruppe; beim Start, beim Laden einer Welt und – weil die Entities
 * nicht persistent sind – beim Laden ihres Chunks werden sie neu angelegt. Ohne Datenbank gelten sie bis zum Neustart.
 *
 * <ul>
 *   <li>{@code /npc create <Skin|-> [Name]} – Skin eines Online-Spielers ({@code -} = Standard), Name als
 *       MiniMessage, Blick zum nächsten Spieler</li>
 *   <li>{@code /npc list} – alle mit Nummer und Position</li>
 *   <li>{@code /npc remove [Nummer]} – ohne Nummer der nächste im Umkreis von {@value #REMOVE_RANGE} Blöcken</li>
 * </ul>
 */
final class NpcAdminCommand implements Listener {

    /** Recht für {@code /npc}. */
    static final String PERMISSION = BuildInfo.BRAND_ID + ".npc.admin";
    /** Umkreis für {@code /npc remove} ohne Nummer. */
    static final double REMOVE_RANGE = 5.0;
    /** Platzhalter für den Standard-Skin. */
    static final String DEFAULT_SKIN = "-";
    /** Name der Skin-Property. */
    static final String TEXTURES = "textures";

    /** Ein angelegter NPC mit gespeichertem Datensatz und (falls gespawnt) Entity. */
    static final class Placed {
        final StoredNpc record;
        NpcService.@Nullable Npc npc;

        Placed(StoredNpc record, NpcService.@Nullable Npc npc) {
            this.record = record;
            this.npc = npc;
        }
    }

    private final NpcService service;
    private final Texts texts;
    private final ServiceRegistry services;
    private final MainThreadExecutor mainThread;
    private final Logger log;
    private final String group;
    private final Map<Long, Placed> placed = new ConcurrentSkipListMap<>();
    /** IDs ohne Datenbank (nur bis zum Neustart), negativ gezählt, damit sie nie mit gespeicherten kollidieren. */
    private final AtomicLong localIds = new AtomicLong();

    NpcAdminCommand(NpcService service, Texts texts, ServiceRegistry services, MainThreadExecutor mainThread,
            Logger log, String group) {
        this.service = Objects.requireNonNull(service, "service");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.services = Objects.requireNonNull(services, "services");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.log = Objects.requireNonNull(log, "log");
        this.group = Objects.requireNonNull(group, "group");
    }

    void register(PaperCommandManager<Source> manager) {
        var npc = manager.commandBuilder("npc").permission(PERMISSION).senderType(PlayerSource.class);
        manager.command(npc.literal("create").required("skin", stringParser())
                .optional("name", greedyStringParser())
                .handler(ctx -> mainThread.execute(() -> create(ctx.sender().source(), ctx.get("skin"),
                        ctx.<String>optional("name").orElse(null)))));
        manager.command(npc.literal("list").handler(ctx -> mainThread.execute(() -> list(ctx.sender().source()))));
        manager.command(npc.literal("remove").handler(ctx -> mainThread.execute(() ->
                removeNearest(ctx.sender().source()))));
        manager.command(npc.literal("remove").required("id", integerParser(1))
                .handler(ctx -> mainThread.execute(() -> remove(ctx.sender().source(),
                        ctx.<Integer>get("id").longValue()))));
    }

    /** Lädt die gespeicherten NPCs der Servergruppe und legt sie in geladenen Welten an. */
    void load() {
        Optional<NpcStore> store = services.find(NpcStore.class);
        if (store.isEmpty()) {
            return;
        }
        mainThread.accept(store.get().list(group).exceptionally(error -> {
            log.warn("NPCs der Gruppe {} nicht geladen: {}", group, error.toString());
            return List.of();
        }), "NPCs laden", records -> {
            for (StoredNpc record : records) {
                Placed entry = new Placed(record, null);
                placed.put(record.id(), entry);
                spawn(entry);
            }
            if (!records.isEmpty()) {
                log.info("{} NPC(s) der Gruppe {} geladen", records.size(), group);
            }
        });
    }

    /** Entfernt alle Entities (beim Abschalten); die Datensätze bleiben. */
    void clear() {
        placed.values().forEach(entry -> {
            NpcService.Npc npc = entry.npc;
            if (npc != null) {
                npc.remove();
            }
        });
        placed.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        placed.values().stream().filter(entry -> !alive(entry) && entry.record.world().equals(event.getWorld()
                .getName())).forEach(this::spawn);
    }

    /** Nicht persistente Entities verschwinden beim Entladen des Chunks; beim Laden kommen sie zurück. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Placed entry : placed.values()) {
            StoredNpc record = entry.record;
            if (!alive(entry) && record.world().equals(chunk.getWorld().getName())
                    && ((int) Math.floor(record.x())) >> 4 == chunk.getX()
                    && ((int) Math.floor(record.z())) >> 4 == chunk.getZ()) {
                spawn(entry);
            }
        }
    }

    private static boolean alive(Placed entry) {
        NpcService.Npc npc = entry.npc;
        return npc != null && npc.entity().isValid();
    }

    /** Legt die Entity zu einem Datensatz an (Welt muss geladen sein). */
    private void spawn(Placed entry) {
        StoredNpc record = entry.record;
        World world = Bukkit.getWorld(record.world());
        if (world == null) {
            return;
        }
        NpcService.Npc previous = entry.npc;
        if (previous != null) {
            previous.remove();
        }
        Location location = new Location(world, record.x(), record.y(), record.z(), record.yaw(), record.pitch());
        entry.npc = service.spawn(new NpcService.NpcSpec(location, profile(record), name(record), record.lookAt(),
                null, null));
    }

    /** Skin aus dem Datensatz (signierte {@code textures}-Property) oder {@code null} (Standard-Skin). */
    private static @Nullable PlayerProfile profile(StoredNpc record) {
        String value = record.skinValue();
        if (value == null) {
            return null;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), record.skinName() == null ? "NPC"
                : record.skinName());
        profile.setProperty(new ProfileProperty(TEXTURES, value, record.skinSignature()));
        return profile;
    }

    private static @Nullable Component name(StoredNpc record) {
        String name = record.name();
        return name == null ? null : MiniMessage.miniMessage().deserialize(name);
    }

    private void create(Player player, String skin, @Nullable String nameInput) {
        String skinName = null;
        String value = null;
        String signature = null;
        if (!DEFAULT_SKIN.equals(skin)) {
            Player source = Bukkit.getPlayerExact(skin);
            if (source == null) {
                texts.send(player, "error.player.unknown", Placeholder.unparsed("player", skin));
                return;
            }
            skinName = source.getName();
            for (ProfileProperty property : source.getPlayerProfile().getProperties()) {
                if (TEXTURES.equals(property.getName())) {
                    value = property.getValue();
                    signature = property.getSignature();
                }
            }
        }
        String name = nameInput == null || nameInput.isBlank() ? null : nameInput.strip();
        Location at = player.getLocation();
        StoredNpc record = new StoredNpc(0, group, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), at.getPitch(), name, skinName, value, signature, true, player.getUniqueId());
        Optional<NpcStore> store = services.find(NpcStore.class);
        CompletableFuture<StoredNpc> saved = store.map(found -> found.create(record))
                .orElseGet(() -> CompletableFuture.completedFuture(record.withId(-localIds.incrementAndGet())));
        mainThread.accept(saved.exceptionally(error -> {
            log.warn("NPC nicht gespeichert: {}", error.toString());
            return null;
        }), "NPC speichern", stored -> {
            if (stored == null) {
                texts.send(player, "error.data");
                return;
            }
            Placed entry = new Placed(stored, null);
            placed.put(stored.id(), entry);
            spawn(entry);
            texts.send(player, store.isPresent() ? "npc.created" : "npc.created.temp", Placeholder.unparsed("id",
                    display(stored.id())));
        });
    }

    private void list(Player player) {
        List<Placed> all = new ArrayList<>(placed.values());
        if (all.isEmpty()) {
            texts.send(player, "npc.list.empty");
            return;
        }
        texts.send(player, "npc.list.header", Placeholder.unparsed("n", Integer.toString(all.size())));
        for (Placed entry : all) {
            StoredNpc record = entry.record;
            Component name = name(record);
            texts.send(player, "npc.list.line", Placeholder.unparsed("id", display(record.id())),
                    Placeholder.component("name", name == null ? Component.text(record.skinName() == null
                            ? DEFAULT_SKIN : record.skinName()) : name),
                    Placeholder.unparsed("world", record.world()),
                    Placeholder.unparsed("x", Integer.toString((int) Math.floor(record.x()))),
                    Placeholder.unparsed("y", Integer.toString((int) Math.floor(record.y()))),
                    Placeholder.unparsed("z", Integer.toString((int) Math.floor(record.z()))));
        }
    }

    private void removeNearest(Player player) {
        Location here = player.getLocation();
        Optional<Placed> nearest = nearest(placed.values(), entry -> {
            StoredNpc record = entry.record;
            return here.getWorld() != null && here.getWorld().getName().equals(record.world())
                    ? here.toVector().distance(new org.bukkit.util.Vector(record.x(), record.y(), record.z()))
                    : Double.POSITIVE_INFINITY;
        }, REMOVE_RANGE);
        if (nearest.isEmpty()) {
            texts.send(player, "npc.none");
            return;
        }
        remove(player, nearest.get().record.id());
    }

    private void remove(Player player, long id) {
        long key = placed.containsKey(id) ? id : -id;
        Placed entry = placed.remove(key);
        if (entry == null) {
            texts.send(player, "npc.none");
            return;
        }
        NpcService.Npc npc = entry.npc;
        if (npc != null) {
            npc.remove();
        }
        if (key > 0) {
            services.find(NpcStore.class).ifPresent(store -> store.delete(key).exceptionally(error -> {
                log.warn("NPC #{} nicht gelöscht: {}", key, error.toString());
                return false;
            }));
        }
        texts.send(player, "npc.removed", Placeholder.unparsed("id", display(key)));
    }

    /** Anzeige-Nummer: gespeicherte NPCs mit ihrer ID, vorläufige (ohne Datenbank) mit ihrer Zählnummer. */
    static String display(long id) {
        return Long.toString(Math.abs(id));
    }

    /** Nächster Eintrag mit Abstand ≤ {@code range}; bei Gleichstand der zuerst gelistete. */
    static <T> Optional<T> nearest(Collection<T> entries, ToDoubleFunction<T> distance, double range) {
        T best = null;
        double bestDistance = range;
        for (T entry : entries) {
            double value = distance.applyAsDouble(entry);
            if (value <= bestDistance && (best == null || value < bestDistance)) {
                best = entry;
                bestDistance = value;
            }
        }
        return Optional.ofNullable(best);
    }
}
