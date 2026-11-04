package net.retromc.paper.core.npc;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.BuildInfo;
import net.retromc.paper.core.lang.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jspecify.annotations.Nullable;

/**
 * Admin-Befehl {@code /npc} für den {@link NpcService} (Spec 02 §8.1): legt Spieler-NPCs an der eigenen Position an,
 * listet und entfernt sie. Die NPCs sind wie der Dienst nicht dauerhaft (bis zum Neustart); feste NPCs der Lobby legt
 * {@code /lobbysetup} in {@code lobby.anchor} an (Spec 03 §2).
 *
 * <ul>
 *   <li>{@code /npc create <Skin|-> [Name]} – Skin eines Online-Spielers ({@code -} = Standard), Name als
 *       MiniMessage, Blick zum nächsten Spieler</li>
 *   <li>{@code /npc list} – alle mit Nummer und Position</li>
 *   <li>{@code /npc remove [Nummer]} – ohne Nummer der nächste im Umkreis von {@value #REMOVE_RANGE} Blöcken</li>
 * </ul>
 */
final class NpcAdminCommand {

    /** Recht für {@code /npc}. */
    static final String PERMISSION = BuildInfo.BRAND_ID + ".npc.admin";
    /** Umkreis für {@code /npc remove} ohne Nummer. */
    static final double REMOVE_RANGE = 5.0;
    /** Platzhalter für den Standard-Skin. */
    static final String DEFAULT_SKIN = "-";

    /** Ein über den Befehl angelegter NPC. */
    record Placed(int id, NpcService.Npc npc, @Nullable Component name) {
    }

    private final NpcService service;
    private final Texts texts;
    private final Map<Integer, Placed> placed = new ConcurrentSkipListMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    NpcAdminCommand(NpcService service, Texts texts) {
        this.service = service;
        this.texts = texts;
    }

    void register(PaperCommandManager<Source> manager) {
        var npc = manager.commandBuilder("npc").permission(PERMISSION).senderType(PlayerSource.class);
        manager.command(npc.literal("create").required("skin", stringParser())
                .optional("name", greedyStringParser())
                .handler(ctx -> create(ctx.sender().source(), ctx.get("skin"),
                        ctx.<String>optional("name").orElse(null))));
        manager.command(npc.literal("list").handler(ctx -> list(ctx.sender().source())));
        manager.command(npc.literal("remove").handler(ctx -> removeNearest(ctx.sender().source())));
        manager.command(npc.literal("remove").required("id", integerParser(1))
                .handler(ctx -> remove(ctx.sender().source(), ctx.<Integer>get("id"))));
    }

    /** Entfernt alle angelegten NPCs (beim Abschalten). */
    void clear() {
        placed.values().forEach(entry -> entry.npc().remove());
        placed.clear();
    }

    private void create(Player player, String skin, @Nullable String nameInput) {
        PlayerProfile profile = null;
        if (!DEFAULT_SKIN.equals(skin)) {
            Player source = Bukkit.getPlayerExact(skin);
            if (source == null) {
                texts.send(player, "error.player.unknown", Placeholder.unparsed("player", skin));
                return;
            }
            profile = source.getPlayerProfile();
        }
        Component name = nameInput == null || nameInput.isBlank() ? null
                : MiniMessage.miniMessage().deserialize(nameInput.strip());
        NpcService.Npc npc = service.spawn(new NpcService.NpcSpec(player.getLocation(), profile, name, true, null,
                null));
        int id = nextId.getAndIncrement();
        placed.put(id, new Placed(id, npc, name));
        texts.send(player, "npc.created", Placeholder.unparsed("id", Integer.toString(id)));
    }

    private void list(Player player) {
        List<Placed> all = new ArrayList<>(placed.values());
        if (all.isEmpty()) {
            texts.send(player, "npc.list.empty");
            return;
        }
        texts.send(player, "npc.list.header", Placeholder.unparsed("n", Integer.toString(all.size())));
        for (Placed entry : all) {
            Location at = entry.npc().entity().getLocation();
            texts.send(player, "npc.list.line", Placeholder.unparsed("id", Integer.toString(entry.id())),
                    Placeholder.component("name", entry.name() == null ? Component.text(DEFAULT_SKIN) : entry.name()),
                    Placeholder.unparsed("world", at.getWorld() == null ? "?" : at.getWorld().getName()),
                    Placeholder.unparsed("x", Integer.toString(at.getBlockX())),
                    Placeholder.unparsed("y", Integer.toString(at.getBlockY())),
                    Placeholder.unparsed("z", Integer.toString(at.getBlockZ())));
        }
    }

    private void removeNearest(Player player) {
        Location here = player.getLocation();
        Optional<Placed> nearest = nearest(placed.values(), entry -> {
            Location at = entry.npc().entity().getLocation();
            return at.getWorld() != null && at.getWorld().equals(here.getWorld()) ? at.distance(here)
                    : Double.POSITIVE_INFINITY;
        }, REMOVE_RANGE);
        if (nearest.isEmpty()) {
            texts.send(player, "npc.none");
            return;
        }
        remove(player, nearest.get().id());
    }

    private void remove(Player player, int id) {
        Placed entry = placed.remove(id);
        if (entry == null) {
            texts.send(player, "npc.none");
            return;
        }
        entry.npc().remove();
        texts.send(player, "npc.removed", Placeholder.unparsed("id", Integer.toString(id)));
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
