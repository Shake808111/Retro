package net.retromc.game.framework.loot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * Eintrag einer {@link LootTable}: eine oder mehrere gleichwertige Materialien (eins wird gleichverteilt gewählt),
 * Mengenbereich, Gewicht, Kategorie, Verzauberungsbereiche und Verzauberungen mit Wahrscheinlichkeit.
 *
 * @param id           Schlüssel in der YAML-Datei
 * @param materials    Materialien (≥ 1)
 * @param amount       Menge
 * @param weight       Gewicht (0 = nie zufällig, nur über Garantien)
 * @param category     Kategorie für Garantien oder leer
 * @param enchantments Verzauberung → Stufenbereich (Stufe 0 = keine)
 * @param chances      Verzauberung (Stufe I) → Wahrscheinlichkeit 0–1
 * @param potion       Trank {@code typ:stufe[:sekunden]} oder {@code null}
 */
public record LootEntry(String id, List<String> materials, Range amount, int weight, String category,
        Map<String, Range> enchantments, Map<String, Double> chances, @Nullable String potion) {

    /** Ganzzahlbereich {@code min–max} (beide eingeschlossen). */
    public record Range(int min, int max) {

        public Range {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("Bereich ungültig: " + min + "-" + max);
            }
        }

        /** {@code 16-48} oder {@code 3}. */
        public static Range parse(String text) {
            String value = text.strip();
            int dash = value.indexOf('-', 1);
            if (dash < 0) {
                int single = Integer.parseInt(value);
                return new Range(single, single);
            }
            return new Range(Integer.parseInt(value.substring(0, dash).strip()),
                    Integer.parseInt(value.substring(dash + 1).strip()));
        }

        public int roll(Random random) {
            return min == max ? min : min + random.nextInt(max - min + 1);
        }
    }

    public LootEntry {
        Objects.requireNonNull(id, "id");
        materials = List.copyOf(materials);
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(category, "category");
        enchantments = Map.copyOf(enchantments);
        chances = Map.copyOf(chances);
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("Eintrag " + id + " ohne Material");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("Gewicht ≥ 0: " + id);
        }
        if (amount.min() < 1) {
            throw new IllegalArgumentException("Menge ≥ 1: " + id);
        }
        chances.values().forEach(chance -> {
            if (chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Wahrscheinlichkeit 0–1: " + id);
            }
        });
    }

    /** Würfelt einen Stapel. */
    public LootStack roll(Random random) {
        String material = materials.size() == 1 ? materials.getFirst()
                : materials.get(random.nextInt(materials.size()));
        Map<String, Integer> enchanted = new LinkedHashMap<>();
        enchantments.forEach((enchantment, levels) -> {
            int level = levels.roll(random);
            if (level > 0) {
                enchanted.put(enchantment, level);
            }
        });
        chances.forEach((enchantment, chance) -> {
            if (random.nextDouble() < chance) {
                enchanted.putIfAbsent(enchantment, 1);
            }
        });
        return new LootStack(id, material, amount.roll(random), category, enchanted, potion);
    }
}
