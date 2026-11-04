package net.retromc.skywars.kit;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ein Item eines Kits ({@code kits/skywars.yml}).
 *
 * @param material     Material-Name
 * @param amount       Anzahl
 * @param enchantments Verzauberung → Stufe
 * @param nameKey      Textschlüssel des Item-Namens (z. B. {@code §7Engeltotem}) oder {@code null} (Vanilla-Name)
 * @param durability   verbleibende Haltbarkeit („Haltbarkeit 10“) oder 0 = voll
 * @param unbreakable  unzerbrechlich (Kit-Regel 1: nur wo angegeben)
 * @param potion       Trankwirkung {@code typ:stufe[:sekunden]} oder {@code null}
 */
public record KitItem(String material, int amount, Map<String, Integer> enchantments, @Nullable String nameKey,
        int durability, boolean unbreakable, @Nullable String potion) {

    public KitItem {
        Objects.requireNonNull(material, "material");
        enchantments = Map.copyOf(enchantments);
        if (amount < 1 || durability < 0) {
            throw new IllegalArgumentException("Kit-Item " + material + ": amount ≥ 1, durability ≥ 0");
        }
    }
}
