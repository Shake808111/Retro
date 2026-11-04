package net.retromc.game.framework.loot;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ein gewürfelter Stapel als reine Daten (ohne Bukkit testbar); {@link LootItems} macht daraus ein Item.
 *
 * @param entry        ID des Tabelleneintrags
 * @param material     Material-Name ({@code DIAMOND_SWORD})
 * @param amount       Anzahl (≥ 1)
 * @param category     Kategorie für Garantien ({@code sword}, {@code armor}, {@code blocks}, {@code food}) oder leer
 * @param enchantments Verzauberung → Stufe (nur Stufen ≥ 1)
 * @param potion       Trank {@code typ:stufe[:sekunden]} oder {@code null}
 */
public record LootStack(String entry, String material, int amount, String category, Map<String, Integer> enchantments,
        @Nullable String potion) {

    public LootStack {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(category, "category");
        enchantments = Map.copyOf(enchantments);
        if (amount < 1) {
            throw new IllegalArgumentException("Anzahl ≥ 1: " + amount);
        }
    }
}
