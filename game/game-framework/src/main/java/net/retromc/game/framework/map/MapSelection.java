package net.retromc.game.framework.map;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.map.MapMeta;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankLimits;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.event.ForcemapEvent;
import net.retromc.paper.core.map.MapService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Map der Runde (Spec 03 §8.1 {@code MapSelection}): Auswahl beim Start aus den gültigen Kandidaten
 * ({@code MapService}), optional Abstimmung (Modi mit {@code voting}), {@code /forcemap} nach {@link ForcemapPolicy}
 * und Laden der Welt. Die Wartelobby liegt auf der Map ({@code waitingLobby}) oder in einer eigenen fertigen Welt
 * ({@code markers.waitingWorld}).
 */
public final class MapSelection {

    /** Höchstzahl der Maps in der Abstimmung (Spec 03 §5.11). */
    public static final int VOTE_OPTIONS = 5;

    private final Game game;
    private final Random random = new Random();
    private final Map<UUID, String> votes = new ConcurrentHashMap<>();
    private List<GameMap> valid = List.of();
    private List<GameMap> voteOptions = List.of();
    private @Nullable GameMap current;
    private @Nullable World world;
    private @Nullable World waitingWorld;
    private boolean ready;
    private boolean forced;
    private boolean voteDone;

    public MapSelection(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    /** Liest die Kandidaten, prüft sie und lädt die erste Map. */
    public CompletableFuture<GameMap> init() {
        MapService service = game.services().get(MapService.class);
        List<GameMap> checked = new ArrayList<>();
        List<MapMeta> metas = service.candidates(game.definition().modeId(), game.variant().key(),
                game.variant().maxPlayers());
        for (MapMeta meta : metas) {
            GameMap map;
            try {
                map = new GameMap(meta);
            } catch (RuntimeException e) {
                game.log().error("Map {} deaktiviert: markers ungültig ({})", meta.id(), e.getMessage());
                continue;
            }
            List<String> errors = new ArrayList<>(MapValidator.validate(map, game.definition().teamMode(),
                    game.variant(), game.mode().teamOrder(), map.markers().has("waitingWorld")));
            errors.addAll(game.mode().validateMap(map));
            if (errors.isEmpty()) {
                checked.add(map);
            } else {
                game.log().error("Map {} deaktiviert: {}", meta.id(), String.join("; ", errors));
            }
        }
        checked.sort(Comparator.comparing(GameMap::id));
        valid = List.copyOf(checked);
        if (valid.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Keine gültige Map für "
                    + game.definition().modeId() + " " + game.variant().key() + " in " + service.directory()));
        }
        GameMap first = pickInitial();
        if (game.definition().voting()) {
            List<GameMap> shuffled = new ArrayList<>(valid);
            Collections.shuffle(shuffled, random);
            voteOptions = List.copyOf(shuffled.subList(0, Math.min(VOTE_OPTIONS, shuffled.size())));
        }
        return load(first).thenApply(world -> first);
    }

    private GameMap pickInitial() {
        String fixed = game.settings().fixedMap();
        if (fixed != null) {
            Optional<GameMap> found = byId(fixed);
            if (found.isPresent()) {
                return found.get();
            }
            game.log().warn("Feste Map {} ist nicht gültig, Zufallsauswahl", fixed);
        }
        return valid.get(random.nextInt(valid.size()));
    }

    /** Lädt Welt (und ggf. Wartelobby-Welt) der Map. */
    public CompletableFuture<World> load(GameMap map) {
        ready = false;
        GameMap previous = current;
        World previousWorld = world;
        current = map;
        MapService service = game.services().get(MapService.class);
        CompletableFuture<World> mapWorld = service.prepare(map.id());
        String waitingFolder = GameMap.string(map.markers(), "waitingWorld");
        CompletableFuture<@Nullable World> waiting = waitingFolder == null
                ? CompletableFuture.completedFuture(null) : service.prepareWorld(waitingFolder);
        return mapWorld.thenCombine(waiting, (loaded, lobby) -> {
            world = loaded;
            waitingWorld = lobby;
            ready = true;
            game.onMapLoaded(map, loaded);
            if (previous != null && previousWorld != null && !previous.id().equals(map.id())) {
                Bukkit.unloadWorld(previousWorld, false);
            }
            return loaded;
        }).whenComplete((loaded, error) -> {
            if (error != null) {
                game.log().error("Map {} konnte nicht geladen werden", map.id(), error);
            }
        });
    }

    public GameMap current() {
        GameMap map = current;
        if (map == null) {
            throw new IllegalStateException("Noch keine Map ausgewählt");
        }
        return map;
    }

    public Optional<GameMap> currentIfSet() {
        return Optional.ofNullable(current);
    }

    public boolean ready() {
        return ready;
    }

    public boolean forced() {
        return forced;
    }

    /** Gültige Maps der Variante. */
    public List<GameMap> valid() {
        return valid;
    }

    public Optional<GameMap> byId(String id) {
        return valid.stream().filter(map -> map.id().equalsIgnoreCase(id)
                || map.displayName().equalsIgnoreCase(id)).findFirst();
    }

    public World world() {
        World loaded = world;
        if (loaded == null) {
            throw new IllegalStateException("Map-Welt nicht geladen");
        }
        return loaded;
    }

    /** Ort eines Spawns der Map-Welt. */
    public Location location(MapMeta.Spawn spawn) {
        return new Location(world(), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    /** Wartelobby-Spawn: eigene Welt oder {@code waitingLobby} auf der Map, sonst Zuschauer-Spawn. */
    public Location waitingSpawn() {
        GameMap map = current();
        MapMeta.Spawn spawn = map.meta().waitingLobby() != null ? map.meta().waitingLobby()
                : map.meta().spectatorSpawn();
        World target = waitingWorld != null ? waitingWorld : world();
        return new Location(target, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    public Location spectatorSpawn() {
        return location(current().meta().spectatorSpawn());
    }

    /** {@code true}, wenn {@code location} in den Grenzen der Map liegt. */
    public boolean inBounds(Location location) {
        return location.getWorld() == world && current().meta().bounds()
                .contains(new MapMeta.Position(location.getX(), location.getY(), location.getZ()));
    }

    // ------------------------------------------------------------------------------------------ Abstimmung

    public List<GameMap> voteOptions() {
        return voteOptions;
    }

    public int votes(String mapId) {
        return (int) votes.values().stream().filter(mapId::equals).count();
    }

    public void vote(Player player, GameMap map) {
        votes.put(player.getUniqueId(), map.id());
        game.texts().send(player, "common.vote.done", Placeholder.unparsed("map", map.displayName()));
    }

    /** Wertet die Abstimmung einmal je Runde aus (ab Rest ≤ 10 s). */
    public void finishVoteOnce() {
        if (!voteDone) {
            voteDone = true;
            finishVote();
        }
    }

    /** Wertet die Abstimmung aus (bei Rest 10 s); ohne Stimmen bleibt die Map. */
    public void finishVote() {
        if (!game.definition().voting() || forced || voteOptions.isEmpty() || votes.isEmpty()) {
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        voteOptions.forEach(map -> counts.put(map.id(), votes(map.id())));
        String winner = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(current().id());
        GameMap map = byId(winner).orElse(current());
        game.texts().broadcast("common.vote.result", Placeholder.unparsed("map", map.displayName()),
                Placeholder.unparsed("votes", Integer.toString(counts.getOrDefault(winner, 0))));
        if (!map.id().equals(current().id())) {
            load(map);
        }
    }

    // ------------------------------------------------------------------------------------------ Forcemap

    /** Heutige Nutzungen und Limit eines Spielers ({@code [used, max]}, max −1 = ∞, 0 = kein Recht). */
    public CompletableFuture<int[]> quota(Player player) {
        UUID uuid = player.getUniqueId();
        if (game.privateRound().isOwner(player)) {
            return CompletableFuture.completedFuture(new int[] {0, RankLimits.UNLIMITED});
        }
        String key = ValkeyKeys.forcemapCount(uuid, ForcemapPolicy.day(Instant.now()));
        CompletableFuture<RankInfo> rank = game.services().get(RankService.class).rank(uuid);
        CompletableFuture<String> used = game.redis().get(key).toCompletableFuture();
        return rank.thenCombine(used, (info, count) -> new int[] {count == null ? 0 : Integer.parseInt(count),
                info.limits().forcemapPerDay()});
    }

    /**
     * {@code /forcemap} bzw. {@code /game setmap} ({@code admin}: ohne Recht, Limit und Rundenregel).
     */
    public void forcemap(Player player, String input, boolean admin) {
        Optional<GameMap> target = byId(input);
        boolean unlimited = admin || game.privateRound().isOwner(player);
        CompletableFuture<int[]> quota = unlimited ? CompletableFuture.completedFuture(new int[] {0, -1})
                : quota(player);
        game.mainThread().accept(quota.exceptionally(error -> new int[] {0, 0}), "/forcemap", values -> {
            ForcemapPolicy.Request request = new ForcemapPolicy.Request(game.state(), game.countdownRest(),
                    target.isPresent(), target.map(map -> map.markers().has("forcemap")
                            ? map.markers().get("forcemap").getAsBoolean() : true).orElse(false),
                    target.map(map -> map.id().equals(current().id())).orElse(false), forced, values[1], values[0],
                    unlimited);
            ForcemapPolicy.Outcome outcome = ForcemapPolicy.check(request);
            if (outcome != ForcemapPolicy.Outcome.OK) {
                game.texts().send(player, outcome.key(), Placeholder.unparsed("map", input),
                        Placeholder.unparsed("used", Integer.toString(values[0])),
                        Placeholder.unparsed("max", Integer.toString(values[1])));
                return;
            }
            GameMap map = target.orElseThrow();
            ForcemapEvent event = new ForcemapEvent(player, map);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            if (!unlimited) {
                LocalDate day = ForcemapPolicy.day(Instant.now());
                String key = ValkeyKeys.forcemapCount(player.getUniqueId(), day);
                RedisAsyncCommands<String, String> redis = game.redis();
                redis.incr(key).thenAccept(count -> redis.expire(key, ForcemapPolicy.COUNTER_TTL.toSeconds()));
            }
            if (!admin) {
                forced = true;
            }
            game.mainThread().accept(load(map), "Map laden", world -> {
                game.texts().broadcast("common.forcemap.done", game.texts().name("name", player),
                        Placeholder.unparsed("map", map.displayName()));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    game.sendMapLine(online);
                }
                if (game.state() == GameState.WAITING || game.state() == GameState.STARTING) {
                    game.relocateWaiting();
                }
            });
        });
    }
}
