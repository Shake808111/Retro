package net.retromc.bedwars.spawner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.BedWarsMode;
import net.retromc.bedwars.BwTeam;
import net.retromc.bedwars.GoldVote;
import net.retromc.bedwars.event.GeneratorTierChangeEvent;
import net.retromc.bedwars.map.BedWarsMap;
import net.retromc.bedwars.shop.ShopItems;
import net.retromc.bedwars.upgrade.UpgradeType;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.holo.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Ressourcen-Generatoren (Spec 04 §6.1.3, §8.2 {@code GeneratorService}): Kupfer 1 s, Eisen 8 s je Basis (Upgrade
 * „Ressourcen“ −15 % je Level), Gold und Diamant nach {@link GeneratorSchedule} mit Stapel-Obergrenze im Radius 1,5;
 * Hologramme über Diamant- und Gold-Spawnern ({@code Spawnt in n Sekunden}) mit drehendem Block. Leere Teams
 * bekommen keine Spawner (Spec 04 §10 #19).
 */
public final class GeneratorService {

    /** Radius der Obergrenze. */
    static final double CAP_RADIUS = 1.5;
    /** Prüfabstand in Ticks. */
    static final long TICKS = 5;

    private final BedWarsMode mode;
    private final List<Generator> generators = new ArrayList<>();
    private final Map<Resource, Integer> forcedTier = new EnumMap<>(Resource.class);
    private final GeneratorSchedule diamond = GeneratorSchedule.diamond();
    private final GeneratorSchedule gold = GeneratorSchedule.gold();
    private @Nullable BukkitTask task;
    private @Nullable BukkitTask spin;

    /** Ein laufender Spawner. */
    private final class Generator {
        final Resource type;
        final @Nullable BwTeam team;
        final Location location;
        final @Nullable GeneratorSchedule schedule;
        final Duration fixed;
        long nextMillis;
        HologramService.@Nullable Hologram hologram;
        @Nullable ItemDisplay display;

        Generator(Resource type, @Nullable BwTeam team, Location location, @Nullable GeneratorSchedule schedule,
                Duration fixed) {
            this.type = type;
            this.team = team;
            this.location = location;
            this.schedule = schedule;
            this.fixed = fixed;
        }

        Duration interval(Duration elapsed) {
            if (schedule == null) {
                double factor = team == null || (type != Resource.COPPER && type != Resource.IRON
                        && type != Resource.BRONZE) ? 1.0
                        : UpgradeType.resourceFactor(team.upgrades().level(UpgradeType.RESOURCES));
                return Duration.ofMillis(Math.max(50, Math.round(fixed.toMillis() * factor)));
            }
            Integer forced = forcedTier.get(type);
            if (forced != null) {
                return schedule.tiers().get(Math.clamp(forced, 1, schedule.tiers().size()) - 1).interval();
            }
            return schedule.interval(elapsed);
        }
    }

    public GeneratorService(BedWarsMode mode) {
        this.mode = mode;
    }

    /** Startet alle Spawner der Map. */
    public void start(Collection<BwTeam> teams) {
        stop();
        BedWarsMap map = mode.map();
        for (BedWarsMap.Spawner spawner : map.spawners()) {
            BwTeam team = null;
            if (spawner.team() != null) {
                team = teams.stream().filter(candidate -> candidate.team().id().equals(spawner.team())).findFirst()
                        .orElse(null);
                if (team == null || team.team().members().isEmpty()) {
                    continue;
                }
            }
            Generator generator = create(spawner, team);
            if (generator != null) {
                generators.add(generator);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(mode.plugin(), this::tick, TICKS, TICKS);
        spin = Bukkit.getScheduler().runTaskTimer(mode.plugin(), this::rotate, 2L, 2L);
    }

    private @Nullable Generator create(BedWarsMap.Spawner spawner, @Nullable BwTeam team) {
        Location location = mode.game().location(spawner.position());
        boolean classic = mode.classic();
        Generator generator = switch (spawner.type()) {
            case COPPER -> new Generator(Resource.COPPER, team, location, null, mode.config().generator("copper", 1));
            case BRONZE -> new Generator(Resource.BRONZE, team, location, null, mode.config().generator("bronze", 1));
            case IRON -> new Generator(Resource.IRON, team, location, null, classic
                    ? mode.config().generator("classic-iron", 10) : mode.config().generator("iron", 8));
            case GOLD -> {
                if (classic) {
                    GoldVote.Rate rate = mode.goldVote().result();
                    yield rate.seconds() == 0 ? null : new Generator(Resource.GOLD, team, location, null,
                            Duration.ofSeconds(rate.seconds()));
                }
                yield new Generator(Resource.GOLD, team, location, gold, Duration.ZERO);
            }
            case DIAMOND -> classic ? null : new Generator(Resource.DIAMOND, team, location, diamond, Duration.ZERO);
        };
        if (generator == null) {
            return null;
        }
        generator.nextMillis = generator.interval(Duration.ZERO).toMillis();
        if (team == null && (generator.type == Resource.DIAMOND || generator.type == Resource.GOLD)) {
            decorate(generator);
        }
        return generator;
    }

    /** Hologramm {@code §bDiamant / §eSpawnt in n Sekunden} und drehender Block (Spec 04 §4.9). */
    private void decorate(Generator generator) {
        Location top = generator.location.clone().add(0, 2.6, 0);
        mode.game().services().find(HologramService.class).ifPresent(holograms -> generator.hologram =
                holograms.global(top, () -> lines(generator)));
        Material block = generator.type == Resource.DIAMOND ? Material.DIAMOND_BLOCK : Material.GOLD_BLOCK;
        generator.display = generator.location.getWorld().spawn(generator.location.clone().add(0, 2.0, 0),
                ItemDisplay.class, display -> {
                    display.setItemStack(new ItemStack(block));
                    display.setBillboard(Display.Billboard.FIXED);
                    display.setPersistent(false);
                });
    }

    private List<Component> lines(Generator generator) {
        Locale locale = Messages.DEFAULT_LOCALE;
        long millis = Math.max(0, generator.nextMillis - mode.game().elapsed().toMillis());
        long seconds = (millis + 999) / 1000;
        String title = generator.type == Resource.DIAMOND ? "bw.holo.diamond" : "bw.holo.gold";
        return List.of(mode.texts().i18n().render(locale, title), mode.texts().i18n().render(locale,
                mode.texts().count("bw.holo.spawn", seconds), Placeholder.unparsed("n", Long.toString(seconds))));
    }

    private void tick() {
        long elapsed = mode.game().elapsed().toMillis();
        for (Generator generator : generators) {
            if (elapsed < generator.nextMillis) {
                continue;
            }
            spawn(generator);
            generator.nextMillis = elapsed + generator.interval(Duration.ofMillis(elapsed)).toMillis();
        }
    }

    private void spawn(Generator generator) {
        int nearby = 0;
        Material material = ShopItems.material(generator.type.material());
        for (Item item : generator.location.getNearbyEntitiesByType(Item.class, CAP_RADIUS)) {
            if (item.getItemStack().getType() == material) {
                nearby += item.getItemStack().getAmount();
            }
        }
        if (nearby >= generator.type.cap()) {
            return;
        }
        ItemStack stack = ShopItems.resource(generator.type, 1, mode.texts().i18n(), Messages.DEFAULT_LOCALE);
        generator.location.getWorld().dropItem(generator.location.clone().add(0, 0.25, 0), stack,
                item -> item.setVelocity(new Vector()));
    }

    private void rotate() {
        for (Generator generator : generators) {
            ItemDisplay display = generator.display;
            if (display != null && display.isValid()) {
                Location location = display.getLocation();
                location.setYaw(location.getYaw() + 9f);
                display.teleport(location);
            }
        }
    }

    /** Jede Sekunde: Stufenwechsel melden ({@code bw.spawner.tier}) und Hologramme neu zeichnen. */
    public void second(Duration elapsed) {
        if (!mode.classic()) {
            for (GeneratorSchedule schedule : List.of(diamond, gold)) {
                int tier = schedule.tierStartingAt(elapsed);
                if (tier > 0 && !forcedTier.containsKey(schedule.resource())) {
                    announce(schedule.resource(), tier, tier == schedule.tiers().size());
                }
            }
        }
        for (Generator generator : generators) {
            HologramService.Hologram hologram = generator.hologram;
            if (hologram != null) {
                hologram.refresh();
            }
        }
    }

    private void announce(Resource resource, int tier, boolean max) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Locale locale = mode.texts().locale(player);
            mode.texts().send(player, "bw.spawner.tier", net.retromc.platform.common.text.ColorText.styling(
                    "res_color", resource.color()), Placeholder.component("res", mode.texts().i18n().render(locale,
                            resource.nameKey())), Placeholder.unparsed("n", Integer.toString(tier)));
        }
        Bukkit.getPluginManager().callEvent(new GeneratorTierChangeEvent(resource, tier, max));
    }

    /** {@code /bwdebug spawner <res> <tier>}: Stufe sofort setzen. */
    public boolean forceTier(Resource resource, int tier) {
        GeneratorSchedule schedule = resource == Resource.DIAMOND ? diamond : resource == Resource.GOLD ? gold : null;
        if (schedule == null || tier < 1 || tier > schedule.tiers().size()) {
            return false;
        }
        forcedTier.put(resource, tier);
        announce(resource, tier, tier == schedule.tiers().size());
        return true;
    }

    /** Anzeige-Stand für das Board (Spec 04 §4.2). */
    public GeneratorSchedule.State state(Resource resource, Duration elapsed) {
        GeneratorSchedule schedule = resource == Resource.DIAMOND ? diamond : gold;
        Integer forced = forcedTier.get(resource);
        if (forced != null) {
            boolean max = forced >= schedule.tiers().size();
            Duration until = max ? Duration.ZERO : schedule.tiers().get(forced).from().minus(elapsed);
            return new GeneratorSchedule.State(forced, max ? forced : forced + 1,
                    until.isNegative() ? Duration.ZERO : until, max);
        }
        return schedule.at(elapsed);
    }

    public void stop() {
        BukkitTask current = task;
        if (current != null) {
            current.cancel();
        }
        BukkitTask spinning = spin;
        if (spinning != null) {
            spinning.cancel();
        }
        task = null;
        spin = null;
        for (Generator generator : generators) {
            if (generator.hologram != null) {
                generator.hologram.remove();
            }
            if (generator.display != null) {
                generator.display.remove();
            }
        }
        generators.clear();
    }
}
