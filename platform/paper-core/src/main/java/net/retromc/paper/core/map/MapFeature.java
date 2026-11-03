package net.retromc.paper.core.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.retromc.core.api.map.MapMeta;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link MapService}: Map-Ordner {@code <dir>/<mapId>/} mit Welt-Dateien und {@code map.json}
 * (Spec 08 §6.11). Ungültige Maps (JSON, Pflichtfelder, {@code id} ≠ Ordner, Lizenz ≠ {@code own}) werden mit
 * {@code ERROR} übersprungen. Geladene Kopien heißen {@code map_<ordner>}; Sitzungs- und Spielerdaten werden nicht
 * kopiert (§6.11 Nr. 2).
 */
public final class MapFeature implements CoreFeature<PaperCore>, MapService {

    /** Umgebungsvariable für den Map-Ordner. */
    public static final String ENV_MAP_DIR = "NETWORK_MAP_DIR";
    /** Name der Konfigurationsdatei im Map-Ordner. */
    public static final String MAP_FILE = "map.json";
    /** Präfix der geladenen Kopien im Welt-Container. */
    public static final String WORLD_PREFIX = "map_";

    private static final Set<String> SKIPPED = Set.of(MAP_FILE, "uid.dat", "session.lock", "playerdata", "stats",
            "advancements", "datapacks");

    private final Map<String, MapMeta> maps = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<World>> loading = new ConcurrentHashMap<>();
    private @Nullable PaperCore core;
    private @Nullable Path directory;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Maps";
    }

    @Override
    public void enable(PaperCore context) throws IOException {
        core = context;
        String configured = System.getenv(ENV_MAP_DIR);
        Path dir = configured != null && !configured.isBlank() ? Path.of(configured)
                : Bukkit.getWorldContainer().toPath().resolve("maps");
        Files.createDirectories(dir);
        directory = dir;
        reload();
        registration = context.services().registerDefault(MapService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        maps.clear();
        loading.clear();
        core = null;
    }

    @Override
    public List<MapMeta> candidates(String mode, String variant, int players) {
        return maps(mode).stream().filter(meta -> meta.supportsVariant(variant) && meta.fits(players)).toList();
    }

    @Override
    public List<MapMeta> maps(String mode) {
        return maps.values().stream().filter(meta -> meta.mode().equals(mode))
                .sorted(Comparator.comparing(MapMeta::id)).toList();
    }

    @Override
    public Optional<MapMeta> meta(String mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    @Override
    public CompletableFuture<World> prepare(String mapId) {
        if (!maps.containsKey(mapId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unbekannte Map: " + mapId));
        }
        return prepareWorld(mapId);
    }

    @Override
    public CompletableFuture<World> prepareWorld(String folder) {
        Path source = directory().resolve(folder);
        if (!Files.isDirectory(source)) {
            World existing = Bukkit.getWorld(folder);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            if (Files.isDirectory(Bukkit.getWorldContainer().toPath().resolve(folder))) {
                return context().mainThread().resume(CompletableFuture.completedFuture(null))
                        .thenApply(ignored -> create(folder));
            }
            return CompletableFuture.failedFuture(new IllegalArgumentException("Welt nicht gefunden: " + folder));
        }
        String worldName = WORLD_PREFIX + folder;
        return loading.computeIfAbsent(worldName, name -> {
            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                return CompletableFuture.completedFuture(loaded);
            }
            Path target = Bukkit.getWorldContainer().toPath().resolve(name);
            CompletableFuture<Void> copied = context().async().run(() -> copyWorld(source, target));
            return context().mainThread().resume(copied).thenApply(ignored -> create(name));
        });
    }

    @Override
    public Path directory() {
        Path dir = directory;
        if (dir == null) {
            throw new IllegalStateException("Maps nicht aktiv");
        }
        return dir;
    }

    @Override
    public void reload() {
        Map<String, MapMeta> found = new ConcurrentHashMap<>();
        try (Stream<Path> folders = Files.list(directory())) {
            for (Path folder : folders.filter(Files::isDirectory).sorted().toList()) {
                Path file = folder.resolve(MAP_FILE);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String id = folder.getFileName().toString();
                try {
                    MapMeta meta = MapJson.parse(Files.readString(file, StandardCharsets.UTF_8));
                    List<String> errors = check(meta, id);
                    if (errors.isEmpty()) {
                        found.put(meta.id(), meta);
                    } else {
                        context().log().error("Map {} deaktiviert: {}", id, String.join("; ", errors));
                    }
                } catch (IOException | IllegalArgumentException e) {
                    context().log().error("Map {} deaktiviert: {}", id, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Map-Ordner nicht lesbar: " + directory(), e);
        }
        maps.clear();
        maps.putAll(found);
        context().log().info("{} Map(s) aus {} geladen", found.size(), directory());
    }

    /** Formale Prüfungen des Ladevorgangs (Spec 08 §6.11 Nr. 1, §6.19). */
    static List<String> check(MapMeta meta, String folder) {
        List<String> errors = new ArrayList<>();
        if (!meta.id().equals(folder)) {
            errors.add("id '" + meta.id() + "' ≠ Ordner '" + folder + "'");
        }
        if (!MapMeta.LICENSE_OWN.equals(meta.license())) {
            errors.add("license muss \"own\" sein");
        }
        if (meta.builders().isEmpty()) {
            errors.add("builders ist leer");
        }
        if (meta.displayName().isBlank() || meta.displayName().length() > 24 || meta.displayName().contains("§")) {
            errors.add("displayName: 1–24 Zeichen ohne §-Codes");
        }
        if (!meta.bounds().contains(meta.spectatorSpawn().position())) {
            errors.add("spectatorSpawn liegt außerhalb von bounds");
        }
        return errors;
    }

    private static World create(String name) {
        World world = new WorldCreator(name).createWorld();
        if (world == null) {
            throw new IllegalStateException("Welt konnte nicht geladen werden: " + name);
        }
        world.setAutoSave(false);
        return world;
    }

    /** Kopiert eine Welt ohne Sitzungs- und Spielerdaten; ein altes Ziel wird vorher gelöscht. */
    static void copyWorld(Path source, Path target) {
        try {
            if (Files.exists(target)) {
                try (Stream<Path> old = Files.walk(target)) {
                    for (Path path : old.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(source) && SKIPPED.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!SKIPPED.contains(file.getFileName().toString())) {
                        Files.copy(file, target.resolve(source.relativize(file).toString()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Welt nicht kopierbar: " + source, e);
        }
    }

    private PaperCore context() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Maps nicht aktiv");
        }
        return context;
    }
}
