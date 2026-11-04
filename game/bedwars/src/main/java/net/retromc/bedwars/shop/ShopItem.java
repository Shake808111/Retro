package net.retromc.bedwars.shop;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.retromc.bedwars.spawner.Resource;
import org.jspecify.annotations.Nullable;

/**
 * Eine Ware des Item-Shops (Spec 04 §6.1.6, {@code shop.yml}).
 *
 * @param id        technische ID (Schnellkauf, Textschlüssel {@code bw.item.<id>} und {@code .lore})
 * @param category  Kategorie 1–8
 * @param material  Material; {@code {TEAM}} wird durch die Blockfarbe des Teams ersetzt
 * @param amount    Stapelgröße = gekaufte Menge
 * @param currency  Währung
 * @param price     Preis
 * @param permanent bleibt nach dem Tod ({@code (Item bleibt nach Kauf permanent)})
 * @param group     Stufen-Gruppe ({@code armor}, {@code pickaxe}, {@code axe}, {@code shears}, {@code sword},
 *                  {@code bow}) oder {@code null}
 * @param tier      Stufe innerhalb der Gruppe
 * @param quick     Standard-Schnellkauf-Feld 0–20 oder −1
 * @param special   Sonder-Item ({@code platform}, {@code capsule} …) oder {@code null}
 * @param enchants  Verzauberungen (Schlüssel wie {@code knockback}) mit Stufe
 * @param potion    Trank {@code type:stufe:sekunden} oder {@code null}
 * @param lore      ob es eine Beschreibung {@code bw.item.<id>.lore} gibt
 */
public record ShopItem(String id, int category, String material, int amount, Resource currency, int price,
        boolean permanent, @Nullable String group, int tier, int quick, @Nullable String special,
        Map<String, Integer> enchants, @Nullable String potion, boolean lore) {

    public ShopItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(currency, "currency");
        enchants = Map.copyOf(enchants);
        if (category < 1 || category > 8) {
            throw new IllegalArgumentException(id + ": Kategorie 1–8");
        }
        if (amount < 1 || amount > 64 || price < 0) {
            throw new IllegalArgumentException(id + ": Menge 1–64, Preis ≥ 0");
        }
        if (quick < -1 || quick > 20) {
            throw new IllegalArgumentException(id + ": Schnellkauf-Feld −1 oder 0–20");
        }
    }

    public String nameKey() {
        return "bw.item." + id;
    }

    public String loreKey() {
        return "bw.item." + id + ".lore";
    }

    /** Material für ein Team ({@code {TEAM}} → {@code RED} …). */
    public String material(String teamDye) {
        return material.replace("{TEAM}", teamDye.toUpperCase(Locale.ROOT));
    }

    /** Startausrüstung (Holzspitzhacke), nicht kaufbar. */
    public boolean free() {
        return price == 0;
    }
}
