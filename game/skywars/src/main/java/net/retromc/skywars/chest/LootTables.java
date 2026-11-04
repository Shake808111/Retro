package net.retromc.skywars.chest;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.retromc.game.framework.loot.LootStack;
import net.retromc.game.framework.loot.LootTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Die SkyWars-Loot-Tabellen (Spec 04 §6.2.4): {@code loot/skywars_t1.yml} (Inseln), {@code skywars_t2.yml}
 * (Mitte), {@code skywars_refill1.yml} und {@code skywars_refill2.yml} (Chestrefill Stufe 1/2, je Abschnitt
 * {@code islands} und {@code mid}). Füllungen sind reine Daten ({@link LootStack}), ohne Server testbar.
 *
 * @param tier1    Inseln beim Start
 * @param tier2    Mitte beim Start
 * @param refills  je Refill-Stufe (ab 1) Inseln und Mitte; höhere Stufen nutzen die letzte
 */
public record LootTables(LootTable tier1, LootTable tier2, List<Refill> refills) {

    /** Garantien je Insel (faire Starts): Schwert, Rüstungsteil, Block-Stack, Essen. */
    public static final List<String> ISLAND_GUARANTEES = List.of("sword", "armor", "blocks", "food");
    /** Dateinamen ohne Endung. */
    public static final List<String> FILES = List.of("skywars_t1", "skywars_t2", "skywars_refill1",
            "skywars_refill2");

    /** Tabellen einer Refill-Stufe. */
    public record Refill(LootTable islands, LootTable mid) {
    }

    /** Quelle der Tabellen-Dateien. */
    @FunctionalInterface
    public interface Source {
        Reader open(String name) throws IOException;
    }

    public LootTables {
        Objects.requireNonNull(tier1, "tier1");
        Objects.requireNonNull(tier2, "tier2");
        refills = List.copyOf(refills);
        if (refills.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Refill-Stufe nötig");
        }
    }

    public static LootTables load(Source source) {
        LootTable tier1 = table(source, "skywars_t1");
        LootTable tier2 = table(source, "skywars_t2");
        return new LootTables(tier1, tier2, List.of(refill(source, "skywars_refill1", tier1, tier2),
                refill(source, "skywars_refill2", tier1, tier2)));
    }

    private static LootTable table(Source source, String name) {
        try (Reader reader = source.open(name)) {
            return LootTable.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Loot-Tabelle " + name + " nicht lesbar", e);
        }
    }

    private static Refill refill(Source source, String name, LootTable tier1, LootTable tier2) {
        try (Reader reader = source.open(name)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            Map<String, LootTable> known = Map.of("skywars_t1", tier1, "skywars_t2", tier2);
            return new Refill(section(source, yaml, "islands", known), section(source, yaml, "mid", known));
        } catch (IOException e) {
            throw new UncheckedIOException("Loot-Tabelle " + name + " nicht lesbar", e);
        }
    }

    private static LootTable section(Source source, YamlConfiguration yaml, String key, Map<String, LootTable> known) {
        ConfigurationSection inline = yaml.getConfigurationSection(key);
        if (inline != null) {
            return LootTable.read(inline);
        }
        String reference = Objects.requireNonNull(yaml.getString(key), key + " fehlt");
        LootTable table = known.get(reference);
        return table != null ? table : table(source, reference);
    }

    /** Tabellen einer Refill-Stufe (ab 1; über die letzte hinaus gilt die letzte). */
    public Refill refill(int stage) {
        return refills.get(Math.clamp(stage - 1, 0, refills.size() - 1));
    }

    /** Erstfüllung der Container einer Insel mit Garantien. */
    public List<Map<Integer, LootStack>> fillIsland(int containers, java.util.Random random) {
        return tier1.fill(containers, random, ISLAND_GUARANTEES);
    }

    /** Erstfüllung eines Containers der Mitte. */
    public Map<Integer, LootStack> fillMid(java.util.Random random) {
        return tier2.roll(random);
    }
}
