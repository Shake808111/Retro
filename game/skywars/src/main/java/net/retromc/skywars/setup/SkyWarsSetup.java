package net.retromc.skywars.setup;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapDraft;
import net.retromc.game.framework.map.MapValidator;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.map.MapJson;
import net.retromc.paper.core.map.MapService;
import net.retromc.skywars.SkyWarsMode;
import net.retromc.skywars.map.SkyWarsMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

/**
 * Map-Einrichtung im Spiel (Spec 04 §3 {@code /swsetup}, Recht {@code retromc.admin.mapsetup}) analog zu
 * {@code /bwsetup}: lädt eine fertige Welt aus dem Map-Ordner, setzt Punkte an der eigenen Position bzw. am
 * anvisierten Block und schreibt {@code maps/<id>/map.json}. Beim Speichern entstehen aus den Inseln die
 * Rahmen-Marker {@code teams} und {@code spawns} (Teams {@code 1 … N}, {@code spawns.solo}).
 *
 * <pre>
 * /swsetup create &lt;id&gt;          /swsetup name &lt;Name&gt;   /swsetup builder &lt;Name&gt; | clear
 * /swsetup variants 8x1,16x1   /swsetup players &lt;min&gt; &lt;max&gt;   /swsetup bounds 1|2
 * /swsetup spectator          /swsetup waiting          /swsetup kitsettings
 * /swsetup island &lt;n&gt; [clear]  Spawn an der eigenen Position zu Insel n (je Spieler einer)
 * /swsetup chest &lt;n|mid&gt;      anvisierte Truhe/anvisiertes Fass zu Insel n bzw. zur Mitte
 * /swsetup mid 1|2            Ecken der Mitte-Region
 * /swsetup border [radius]    Border-Zentrum an der eigenen Position, Radius (Standard: aus bounds)
 * /swsetup voidy [y]          /swsetup info              /swsetup save
 * </pre>
 */
public final class SkyWarsSetup {

    /** Recht für {@code /swsetup}. */
    public static final String PERMISSION = BuildInfo.BRAND_ID + ".admin.mapsetup";
    static final List<String> ACTIONS = List.of("create", "name", "builder", "variants", "players", "bounds",
            "spectator", "waiting", "kitsettings", "island", "chest", "mid", "border", "voidy", "info", "save");
    /** Reichweite für anvisierte Blöcke. */
    static final int REACH = 6;

    private record Session(MapDraft draft, World world) {
    }

    private final SkyWarsMode mode;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private SkyWarsSetup(SkyWarsMode mode) {
        this.mode = mode;
    }

    public static void register(SkyWarsMode mode) {
        SkyWarsSetup setup = new SkyWarsSetup(mode);
        PaperCommandManager<Source> manager = mode.game().services().get(CommandService.class).manager();
        manager.command(manager.commandBuilder("swsetup").permission(PERMISSION).senderType(PlayerSource.class)
                .required("action", stringParser(), SuggestionProvider.suggestingStrings(ACTIONS))
                .optional("args", greedyStringParser())
                .handler(ctx -> {
                    Player player = ctx.sender().source();
                    String args = ctx.<String>optional("args").orElse("");
                    mode.game().mainThread().execute(() -> setup.run(player, ctx.get("action"), args));
                }));
    }

    private void run(Player player, String action, String rest) {
        String[] args = rest.isBlank() ? new String[0] : rest.strip().split("\\s+");
        try {
            if (action.equalsIgnoreCase("create")) {
                create(player, args);
                return;
            }
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                send(player, "sw.setup.none");
                return;
            }
            MapDraft draft = session.draft();
            String saved = switch (action.toLowerCase(Locale.ROOT)) {
                case "name" -> {
                    draft.displayName(String.join(" ", args));
                    yield "displayName";
                }
                case "builder" -> {
                    if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
                        draft.clearBuilders();
                    } else {
                        draft.addBuilder(String.join(" ", args));
                    }
                    yield "builders";
                }
                case "variants" -> {
                    draft.variants(Arrays.asList(String.join("", args).split(",")));
                    yield "variants";
                }
                case "players" -> {
                    draft.players(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
                    yield "minPlayers/maxPlayers";
                }
                case "bounds" -> {
                    int corner = Integer.parseInt(args[0]);
                    Location at = player.getLocation();
                    draft.corner(corner, new MapMeta.Position(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
                    yield "bounds." + (corner == 1 ? "min" : "max");
                }
                case "spectator" -> {
                    draft.spectator(spawn(player.getLocation()));
                    yield "spectatorSpawn";
                }
                case "waiting" -> {
                    boolean own = player.getWorld().equals(session.world());
                    draft.waiting(spawn(player.getLocation()), own ? null : folder(player.getWorld()));
                    yield own ? "waitingLobby" : "waitingLobby + waitingWorld";
                }
                case "kitsettings" -> {
                    draft.markers().add("kitSettings", MapDraft.json(spawn(player.getLocation())));
                    yield "kitSettings";
                }
                case "island" -> island(draft, args, player.getLocation());
                case "chest" -> chest(draft, args, player);
                case "mid" -> {
                    int corner = Integer.parseInt(args[0]);
                    Location at = player.getLocation();
                    JsonObject mid = section(draft.markers(), "mid");
                    mid.add(corner == 1 ? "min" : "max", MapDraft.json(at.getBlockX(), at.getBlockY(),
                            at.getBlockZ()));
                    yield "mid." + (corner == 1 ? "min" : "max");
                }
                case "border" -> {
                    JsonObject border = section(draft.markers(), "border");
                    Location at = player.getLocation();
                    border.addProperty("x", at.getBlockX() + 0.5);
                    border.addProperty("z", at.getBlockZ() + 0.5);
                    if (args.length > 0) {
                        border.addProperty("radius", Integer.parseInt(args[0]));
                    }
                    yield "border";
                }
                case "voidy" -> {
                    draft.value("voidY", args.length > 0 ? Integer.parseInt(args[0])
                            : player.getLocation().getBlockY());
                    yield "voidY";
                }
                case "info" -> {
                    info(player, draft);
                    yield null;
                }
                case "save" -> {
                    save(player, draft);
                    yield null;
                }
                default -> {
                    send(player, "sw.setup.usage");
                    yield null;
                }
            };
            if (saved != null) {
                send(player, "sw.setup.saved", Placeholder.unparsed("entry", saved));
            }
        } catch (UnknownIsland e) {
            send(player, "sw.setup.island");
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            send(player, "sw.setup.usage");
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(player, "sw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
        }
    }

    private void create(Player player, String[] args) {
        String id = args[0].toLowerCase(Locale.ROOT);
        MapService maps = mode.game().services().get(MapService.class);
        Path file = maps.directory().resolve(id).resolve("map.json");
        MapDraft draft;
        if (Files.isRegularFile(file)) {
            try {
                draft = MapDraft.of(MapJson.parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException | IllegalArgumentException e) {
                send(player, "sw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
                draft = new MapDraft(id, mode.config().modeId());
            }
        } else {
            draft = new MapDraft(id, mode.config().modeId());
        }
        MapDraft opened = draft;
        mode.game().mainThread().accept(maps.prepareWorld(id).exceptionally(error -> null), "Map-Welt", world -> {
            if (world == null) {
                send(player, "sw.setup.world", Placeholder.unparsed("id", id));
                return;
            }
            sessions.put(player.getUniqueId(), new Session(opened, world));
            player.teleport(world.getSpawnLocation());
            send(player, "sw.setup.created", Placeholder.unparsed("id", id));
        });
    }

    private String island(MapDraft draft, String[] args, Location location) {
        int number = Integer.parseInt(args[0]);
        if (number < 1 || number > 64) {
            throw new UnknownIsland();
        }
        JsonObject island = island(draft, number);
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            island.add("spawns", new JsonArray());
            island.add("chests", new JsonArray());
            return "islands[" + number + "]";
        }
        JsonArray spawns = island.has("spawns") ? island.getAsJsonArray("spawns") : new JsonArray();
        spawns.add(MapDraft.json(spawn(location)));
        island.add("spawns", spawns);
        return "islands[" + number + "].spawns[" + spawns.size() + "]";
    }

    private @Nullable String chest(MapDraft draft, String[] args, Player player) {
        Block block = player.getTargetBlockExact(REACH);
        if (block == null || !(block.getState() instanceof Container)) {
            send(player, "sw.setup.chest");
            return null;
        }
        JsonObject position = MapDraft.json(block.getX(), block.getY(), block.getZ());
        JsonObject target;
        String name;
        if (args[0].equalsIgnoreCase("mid")) {
            target = section(draft.markers(), "mid");
            name = "mid.chests";
        } else {
            int number = Integer.parseInt(args[0]);
            if (number < 1 || number > 64) {
                throw new UnknownIsland();
            }
            target = island(draft, number);
            name = "islands[" + number + "].chests";
        }
        JsonArray chests = target.has("chests") ? target.getAsJsonArray("chests") : new JsonArray();
        if (!chests.contains(position)) {
            chests.add(position);
        }
        target.add("chests", chests);
        return name + "[" + chests.size() + "]";
    }

    /** Insel {@code number} (ab 1); fehlende Inseln davor werden leer angelegt. */
    private static JsonObject island(MapDraft draft, int number) {
        JsonArray islands = draft.markers().has("islands") ? draft.markers().getAsJsonArray("islands")
                : new JsonArray();
        while (islands.size() < number) {
            JsonObject empty = new JsonObject();
            empty.add("spawns", new JsonArray());
            empty.add("chests", new JsonArray());
            islands.add(empty);
        }
        draft.markers().add("islands", islands);
        return islands.get(number - 1).getAsJsonObject();
    }

    private static JsonObject section(JsonObject markers, String name) {
        JsonObject section = GameMap.object(markers, name);
        if (section == null) {
            section = new JsonObject();
            markers.add(name, section);
        }
        return section;
    }

    /** Rahmen-Marker aus den Inseln: {@code teams = ["1" …]}, {@code spawns.{n}} und {@code spawns.solo}. */
    public static void deriveSpawns(JsonObject markers) {
        JsonArray teams = new JsonArray();
        JsonObject spawns = new JsonObject();
        JsonArray solo = new JsonArray();
        JsonArray islands = markers.has("islands") ? markers.getAsJsonArray("islands") : new JsonArray();
        // Insel n = Team n; eine Insel ohne Spawn meldet die Prüfung (Spawns < Teamgröße)
        for (int i = 0; i < islands.size(); i++) {
            String id = Integer.toString(i + 1);
            teams.add(id);
            JsonArray islandSpawns = GameMap.array(islands.get(i).getAsJsonObject(), "spawns");
            if (!islandSpawns.isEmpty()) {
                JsonElement first = islandSpawns.get(0);
                spawns.add(id, first.deepCopy());
                solo.add(first.deepCopy());
            }
        }
        spawns.add("solo", solo);
        markers.add("teams", teams);
        markers.add("spawns", spawns);
    }

    private void info(Player player, MapDraft draft) {
        List<String> missing = new ArrayList<>(draft.missing());
        if (missing.isEmpty()) {
            deriveSpawns(draft.markers());
            missing.addAll(errors(draft.build()));
        }
        send(player, "sw.setup.info", Placeholder.unparsed("id", draft.id()),
                Placeholder.unparsed("name", draft.displayName()),
                Placeholder.unparsed("missing", missing.isEmpty() ? "-" : String.join(", ", missing)));
    }

    private void save(Player player, MapDraft draft) {
        List<String> missing = draft.missing();
        if (!missing.isEmpty()) {
            send(player, "sw.setup.missing", Placeholder.unparsed("missing", String.join(", ", missing)));
            return;
        }
        deriveSpawns(draft.markers());
        MapMeta meta = draft.build();
        MapService maps = mode.game().services().get(MapService.class);
        Path file = maps.directory().resolve(meta.id()).resolve("map.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MapJson.write(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            send(player, "sw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
            return;
        }
        maps.reload();
        send(player, "sw.setup.saved", Placeholder.unparsed("entry", file.toString()));
        List<String> errors = errors(meta);
        if (!errors.isEmpty()) {
            send(player, "sw.setup.invalid", Placeholder.unparsed("errors", String.join(", ", errors)));
        }
    }

    /** Prüfungen von Rahmen und SkyWars für alle Varianten der Map (ohne Liste: die laufende Variante). */
    private List<String> errors(MapMeta meta) {
        return errors(meta, mode.game().variant().key(), key -> mode.game().settings().variant(key));
    }

    /** Prüfungen für alle Varianten der Map ({@code fallback} = Variante, wenn die Map keine nennt). */
    public static List<String> errors(MapMeta meta, String fallback,
            java.util.function.Function<String, GameVariant> variants) {
        GameMap map = new GameMap(meta);
        List<String> keys = meta.variants().isEmpty() ? List.of(fallback) : meta.variants();
        List<String> errors = new ArrayList<>();
        for (String key : keys) {
            GameVariant variant = variants.apply(key);
            TeamMode teamMode = variant.teamSize() == 1 ? TeamMode.SOLO : TeamMode.NUMBERED;
            List<String> found = new ArrayList<>(MapValidator.validate(map, teamMode, variant, List.of(),
                    map.markers().has("waitingWorld")));
            found.addAll(new SkyWarsMap(map).validate(variant));
            found.stream().filter(error -> !errors.contains(error)).forEach(errors::add);
        }
        return errors;
    }

    private static MapMeta.Spawn spawn(Location location) {
        float yaw = Math.round(location.getYaw() / 45f) * 45f;
        return new MapMeta.Spawn(location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5, yaw,
                0f);
    }

    private static String folder(World world) {
        String name = world.getName();
        return name.startsWith("map_") ? name.substring("map_".length()) : name;
    }

    private void send(Player player, String key, TagResolver... args) {
        mode.texts().send(player, key, args);
    }

    /** Unbekannte Inselnummer ({@code sw.setup.island}). */
    private static final class UnknownIsland extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnknownIsland() {
            super(null, null, false, false);
        }
    }
}
