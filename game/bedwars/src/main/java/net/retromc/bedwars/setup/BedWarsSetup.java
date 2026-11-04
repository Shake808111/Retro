package net.retromc.bedwars.setup;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

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
import net.retromc.bedwars.BedWarsMode;
import net.retromc.bedwars.map.BedWarsMap;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapDraft;
import net.retromc.game.framework.map.MapValidator;
import net.retromc.game.framework.team.TeamColor;
import net.retromc.paper.core.command.CommandService;
import net.retromc.paper.core.map.MapJson;
import net.retromc.paper.core.map.MapService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

/**
 * Map-Einrichtung im Spiel (Spec 04 §3 {@code /bwsetup}, Recht {@code retromc.admin.mapsetup}): lädt eine fertige
 * Welt aus dem Map-Ordner, setzt die Punkte an der eigenen Position bzw. am anvisierten Block und schreibt
 * {@code maps/<id>/map.json}. Maps und Wartelobby kommen als fertige Welten; hier werden sie nur konfiguriert.
 *
 * <pre>
 * /bwsetup create &lt;id&gt;            Welt maps/&lt;id&gt; laden, Entwurf öffnen (map.json wird übernommen)
 * /bwsetup name &lt;Anzeigename&gt;     /bwsetup builder &lt;Name&gt; | clear     /bwsetup variants 4x4,8x1
 * /bwsetup players &lt;min&gt; &lt;max&gt;   /bwsetup bounds 1|2     /bwsetup spectator     /bwsetup waiting
 * /bwsetup spawn|shop|upgrade &lt;team&gt;      /bwsetup bed|chest &lt;team&gt; (anvisierter Block)
 * /bwsetup spawner &lt;copper|iron|gold|diamond|bronze&gt; [team]   /bwsetup buildheight [y]   /bwsetup voidy [y]
 * /bwsetup teams &lt;rot,blau,…&gt;    /bwsetup info    /bwsetup save
 * </pre>
 */
public final class BedWarsSetup {

    /** Recht für {@code /bwsetup}. */
    public static final String PERMISSION = BuildInfo.BRAND_ID + ".admin.mapsetup";
    static final List<String> ACTIONS = List.of("create", "name", "builder", "variants", "players", "bounds",
            "spectator", "waiting", "spawn", "bed", "shop", "upgrade", "spawner", "chest", "buildheight", "voidy",
            "teams", "info", "save");
    /** Reichweite für anvisierte Blöcke. */
    static final int REACH = 6;

    /** Offener Entwurf eines Admins. */
    private record Session(MapDraft draft, World world) {
    }

    private final BedWarsMode mode;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private BedWarsSetup(BedWarsMode mode) {
        this.mode = mode;
    }

    public static void register(BedWarsMode mode) {
        BedWarsSetup setup = new BedWarsSetup(mode);
        PaperCommandManager<Source> manager = mode.game().services().get(CommandService.class).manager();
        manager.command(manager.commandBuilder("bwsetup").permission(PERMISSION).senderType(PlayerSource.class)
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
                send(player, "bw.setup.none");
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
                case "spawn" -> point(draft, "spawns", team(args), player.getLocation());
                case "shop" -> point(draft, "shops", team(args), player.getLocation());
                case "upgrade" -> point(draft, "upgrades", team(args), player.getLocation());
                case "bed" -> bed(draft, team(args), player);
                case "chest" -> chest(draft, team(args), player);
                case "spawner" -> spawner(draft, args, player.getLocation());
                case "buildheight" -> {
                    draft.value("buildMaxY", args.length > 0 ? Integer.parseInt(args[0])
                            : player.getLocation().getBlockY());
                    yield "buildMaxY";
                }
                case "voidy" -> {
                    draft.value("voidY", args.length > 0 ? Integer.parseInt(args[0])
                            : player.getLocation().getBlockY());
                    yield "voidY";
                }
                case "teams" -> {
                    com.google.gson.JsonArray teams = new com.google.gson.JsonArray();
                    for (String id : String.join("", args).split(",")) {
                        teams.add(TeamColor.byId(id).orElseThrow(UnknownTeam::new).id());
                    }
                    draft.markers().add("teams", teams);
                    yield "teams";
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
                    send(player, "bw.setup.usage");
                    yield null;
                }
            };
            if (saved != null) {
                send(player, "bw.setup.saved", Placeholder.unparsed("entry", saved));
            }
        } catch (UnknownTeam e) {
            send(player, "bw.setup.team");
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            send(player, "bw.setup.usage");
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(player, "bw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
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
                send(player, "bw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
                draft = new MapDraft(id, mode.config().modeId());
            }
        } else {
            draft = new MapDraft(id, mode.config().modeId());
        }
        MapDraft opened = draft;
        mode.game().mainThread().accept(maps.prepareWorld(id).exceptionally(error -> null), "Map-Welt", world -> {
            if (world == null) {
                send(player, "bw.setup.world", Placeholder.unparsed("id", id));
                return;
            }
            sessions.put(player.getUniqueId(), new Session(opened, world));
            player.teleport(world.getSpawnLocation());
            send(player, "bw.setup.created", Placeholder.unparsed("id", id));
        });
    }

    private String point(MapDraft draft, String group, String team, Location location) {
        draft.put(group, team, MapDraft.json(spawn(location)));
        return group + "." + team;
    }

    private @Nullable String bed(MapDraft draft, String team, Player player) {
        Block block = player.getTargetBlockExact(REACH);
        if (block == null || !(block.getBlockData() instanceof Bed bed)) {
            send(player, "bw.setup.bed");
            return null;
        }
        Block head = bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing());
        Block foot = bed.getPart() == Bed.Part.HEAD ? block.getRelative(bed.getFacing().getOppositeFace()) : block;
        JsonObject value = new JsonObject();
        value.add("head", MapDraft.json(head.getX(), head.getY(), head.getZ()));
        value.add("foot", MapDraft.json(foot.getX(), foot.getY(), foot.getZ()));
        draft.put("beds", team, value);
        return "beds." + team;
    }

    private @Nullable String chest(MapDraft draft, String team, Player player) {
        Block block = player.getTargetBlockExact(REACH);
        if (block == null || !block.getType().name().endsWith("CHEST")) {
            send(player, "bw.setup.chest");
            return null;
        }
        draft.put("chests", team, MapDraft.json(block.getX(), block.getY(), block.getZ()));
        return "chests." + team;
    }

    private String spawner(MapDraft draft, String[] args, Location location) {
        Resource type = Resource.byId(args[0]).orElseThrow(() -> new IllegalArgumentException(args[0]));
        JsonObject value = MapDraft.json(new MapMeta.Spawn(location.getBlockX() + 0.5, location.getBlockY(),
                location.getBlockZ() + 0.5, 0, 0));
        value.addProperty("type", type.id());
        if (args.length > 1) {
            value.addProperty("team", team(new String[] {args[1]}));
        }
        draft.append("spawners", value);
        return "spawners[" + type.id() + "]";
    }

    private void info(Player player, MapDraft draft) {
        List<String> missing = new ArrayList<>(draft.missing());
        if (missing.isEmpty()) {
            missing.addAll(errors(draft.build()));
        }
        send(player, "bw.setup.info", Placeholder.unparsed("id", draft.id()),
                Placeholder.unparsed("name", draft.displayName()),
                Placeholder.unparsed("missing", missing.isEmpty() ? "-" : String.join(", ", missing)));
    }

    private void save(Player player, MapDraft draft) {
        List<String> missing = draft.missing();
        if (!missing.isEmpty()) {
            send(player, "bw.setup.missing", Placeholder.unparsed("missing", String.join(", ", missing)));
            return;
        }
        MapMeta meta = draft.build();
        MapService maps = mode.game().services().get(MapService.class);
        Path file = maps.directory().resolve(meta.id()).resolve("map.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MapJson.write(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            send(player, "bw.setup.error", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
            return;
        }
        maps.reload();
        send(player, "bw.setup.saved", Placeholder.unparsed("entry", file.toString()));
        List<String> errors = errors(meta);
        if (!errors.isEmpty()) {
            send(player, "bw.setup.invalid", Placeholder.unparsed("errors", String.join(", ", errors)));
        }
    }

    /** Prüfungen von Rahmen und BedWars für alle Varianten der Map (ohne Liste: die laufende Variante). */
    private List<String> errors(MapMeta meta) {
        GameMap map = new GameMap(meta);
        List<String> variants = meta.variants().isEmpty() ? List.of(mode.game().variant().key()) : meta.variants();
        List<String> errors = new ArrayList<>();
        for (String key : variants) {
            GameVariant variant = mode.game().settings().variant(key);
            for (String error : MapValidator.validate(map, mode.game().definition().teamMode(), variant,
                    mode.teamOrder(), map.markers().has("waitingWorld"))) {
                if (!errors.contains(error)) {
                    errors.add(error);
                }
            }
            for (String error : new BedWarsMap(map).validate(MapValidator.teamIds(map, variant, mode.teamOrder()),
                    mode.classic())) {
                if (!errors.contains(error)) {
                    errors.add(error);
                }
            }
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

    private static String team(String[] args) {
        return TeamColor.byId(args[0]).orElseThrow(UnknownTeam::new).id();
    }

    private void send(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver...
            args) {
        mode.texts().send(player, key, args);
    }

    /** Unbekannter Teamname ({@code bw.setup.team}). */
    private static final class UnknownTeam extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnknownTeam() {
            super(null, null, false, false);
        }
    }
}
