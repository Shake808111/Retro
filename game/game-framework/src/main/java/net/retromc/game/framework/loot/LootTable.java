package net.retromc.game.framework.loot;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Gewichtete Loot-Tabelle (Spec 04 §6.2.4, §8.1 {@code LootTable.load(yaml)}, {@code fill(…, Random, Guarantees)}):
 * je Container {@code stacks} Stapel in zufälligen Slots; Garantien sorgen dafür, dass eine Gruppe von Containern
 * (z. B. alle Truhen einer Insel) jede verlangte Kategorie mindestens einmal enthält. Ohne Server testbar.
 *
 * <pre>
 * stacks: 5-8
 * entries:
 *   stone_blocks:
 *     materials: [STONE, COBBLESTONE]
 *     amount: 16-48
 *     weight: 10
 *     category: blocks
 *     enchantments: {sharpness: 1-2}
 *     chances: {vanishing_curse: 0.25}
 *     potion: instant_health:2
 * </pre>
 *
 * @param stacks  Stapel je Container
 * @param entries Einträge in Dateireihenfolge
 */
public record LootTable(LootEntry.Range stacks, List<LootEntry> entries) {

    /** Container-Größe einer Truhe bzw. eines Fasses (3 Reihen). */
    public static final int CONTAINER_SIZE = 27;

    public LootTable {
        Objects.requireNonNull(stacks, "stacks");
        entries = List.copyOf(entries);
        if (stacks.max() > CONTAINER_SIZE) {
            throw new IllegalArgumentException("Höchstens " + CONTAINER_SIZE + " Stapel je Container");
        }
        if (entries.stream().noneMatch(entry -> entry.weight() > 0)) {
            throw new IllegalArgumentException("Tabelle ohne Eintrag mit Gewicht > 0");
        }
    }

    /** Liest eine Tabelle aus YAML. */
    public static LootTable load(Reader reader) {
        return read(YamlConfiguration.loadConfiguration(reader));
    }

    /** Liest eine Tabelle aus einem Abschnitt ({@code stacks}, {@code entries}). */
    public static LootTable read(ConfigurationSection section) {
        LootEntry.Range stacks = LootEntry.Range.parse(String.valueOf(section.get("stacks", "5-8")));
        ConfigurationSection list = section.getConfigurationSection("entries");
        if (list == null) {
            throw new IllegalArgumentException("entries fehlt");
        }
        List<LootEntry> entries = new ArrayList<>();
        for (String id : list.getKeys(false)) {
            ConfigurationSection entry = Objects.requireNonNull(list.getConfigurationSection(id), id);
            List<String> materials = entry.isList("materials") ? entry.getStringList("materials")
                    : List.of(Objects.requireNonNull(entry.getString("material"), id + ".material"));
            entries.add(new LootEntry(id, materials.stream().map(value -> value.toUpperCase(Locale.ROOT)).toList(),
                    LootEntry.Range.parse(String.valueOf(entry.get("amount", "1"))), entry.getInt("weight", 0),
                    entry.getString("category", ""), ranges(entry.getConfigurationSection("enchantments")),
                    chances(entry.getConfigurationSection("chances")), entry.getString("potion")));
        }
        return new LootTable(stacks, entries);
    }

    private static Map<String, LootEntry.Range> ranges(@Nullable ConfigurationSection section) {
        Map<String, LootEntry.Range> result = new LinkedHashMap<>();
        if (section != null) {
            section.getKeys(false).forEach(key -> result.put(key, LootEntry.Range.parse(String.valueOf(
                    section.get(key)))));
        }
        return result;
    }

    private static Map<String, Double> chances(@Nullable ConfigurationSection section) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (section != null) {
            section.getKeys(false).forEach(key -> result.put(key, section.getDouble(key)));
        }
        return result;
    }

    /** Ob die Tabelle Einträge der Kategorie hat. */
    public boolean has(String category) {
        return entries.stream().anyMatch(entry -> entry.category().equals(category));
    }

    /** Alle Materialien, die zufällig (Gewicht &gt; 0) vorkommen können. */
    public Set<String> possibleMaterials() {
        Set<String> materials = new LinkedHashSet<>();
        entries.stream().filter(entry -> entry.weight() > 0).forEach(entry -> materials.addAll(entry.materials()));
        return materials;
    }

    /** Wählt einen Eintrag nach Gewicht unter {@code filter} (alle Gewichte 0 → gleichverteilt). */
    public @Nullable LootEntry pick(Random random, Predicate<LootEntry> filter) {
        List<LootEntry> candidates = entries.stream().filter(filter).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        int total = candidates.stream().mapToInt(LootEntry::weight).sum();
        if (total <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        int roll = random.nextInt(total);
        for (LootEntry entry : candidates) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return candidates.getLast();
    }

    /** Würfelt den Inhalt eines Containers: {@code stacks} Stapel in zufälligen, verschiedenen Slots. */
    public Map<Integer, LootStack> roll(Random random) {
        return roll(random, CONTAINER_SIZE);
    }

    public Map<Integer, LootStack> roll(Random random, int size) {
        int count = Math.min(size, stacks.roll(random));
        List<Integer> slots = shuffledSlots(random, size);
        Map<Integer, LootStack> content = new HashMap<>();
        for (int i = 0; i < count; i++) {
            LootEntry entry = Objects.requireNonNull(pick(random, candidate -> candidate.weight() > 0));
            content.put(slots.get(i), entry.roll(random));
        }
        return content;
    }

    /**
     * Füllt eine Gruppe von {@code containers} Containern; jede Kategorie aus {@code guarantees}, die die Tabelle
     * kennt, kommt danach in der Gruppe mindestens einmal vor (fehlende werden in einen freien Slot eines zufälligen
     * Containers gelegt).
     */
    public List<Map<Integer, LootStack>> fill(int containers, Random random, Collection<String> guarantees) {
        List<Map<Integer, LootStack>> result = new ArrayList<>();
        for (int i = 0; i < containers; i++) {
            result.add(roll(random));
        }
        if (containers == 0) {
            return result;
        }
        for (String category : guarantees) {
            boolean present = result.stream().anyMatch(content -> content.values().stream()
                    .anyMatch(stack -> stack.category().equals(category)));
            LootEntry entry = present ? null : pick(random, candidate -> candidate.category().equals(category));
            if (entry == null) {
                continue;
            }
            Map<Integer, LootStack> target = result.get(random.nextInt(containers));
            List<Integer> free = shuffledSlots(random, CONTAINER_SIZE).stream()
                    .filter(slot -> !target.containsKey(slot)).toList();
            if (!free.isEmpty()) {
                target.put(free.getFirst(), entry.roll(random));
            }
        }
        return result;
    }

    private static List<Integer> shuffledSlots(Random random, int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            slots.add(slot);
        }
        java.util.Collections.shuffle(slots, random);
        return slots;
    }
}
