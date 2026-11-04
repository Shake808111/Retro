package net.retromc.skywars.kit;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ein SkyWars-Kit aus dem Katalog (Spec 04 §6.2.5).
 *
 * @param id      technische ID ({@code engel})
 * @param price   Preis in Coins (0 = kostenlos, immer im Besitz)
 * @param icon    Material im Kit-Menü
 * @param items   Ausrüstung
 * @param ability Fähigkeit oder {@code null}
 * @param legacy  früheres Kit (nur mit „Frühere Kits“ an)
 * @param classic auch im Classic-Preset (Item-Kits und frühere Kits, Spec 04 §6.2.7)
 */
public record KitDef(String id, long price, String icon, List<KitItem> items, @Nullable Ability ability,
        boolean legacy, boolean classic) {

    /**
     * Fähigkeit eines Kits.
     *
     * @param type     Art
     * @param uses     Nutzungen je Runde (0 = unbegrenzt)
     * @param cooldown Abklingzeit in Sekunden
     * @param item     Fähigkeits-Item (Slot 9) oder {@code null} bei passiven Fähigkeiten
     * @param consume  das Item wird je Nutzung verbraucht (Spawneier, Windkugeln)
     */
    public record Ability(AbilityType type, int uses, int cooldown, @Nullable KitItem item, boolean consume) {

        public Ability {
            Objects.requireNonNull(type, "type");
            if (uses < 0 || cooldown < 0) {
                throw new IllegalArgumentException("uses/cooldown ≥ 0");
            }
            if (type.active() && item == null) {
                throw new IllegalArgumentException("Aktive Fähigkeit " + type + " ohne Item");
            }
        }
    }

    public KitDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(icon, "icon");
        items = List.copyOf(items);
        if (!id.matches("[a-z0-9_]{1,32}") || id.equals("random")) {
            throw new IllegalArgumentException("Kit-ID [a-z0-9_]{1,32}, nicht random: " + id);
        }
        if (price < 0) {
            throw new IllegalArgumentException("Preis ≥ 0: " + id);
        }
    }

    /** Textschlüssel des Namens. */
    public String nameKey() {
        return "sw.kit." + id;
    }

    /** Textschlüssel der Beschreibung der Fähigkeit. */
    public String descKey() {
        return "sw.kit." + id + ".desc";
    }

    public boolean free() {
        return price == 0;
    }

    /** Hat eine Fähigkeit, die sich mit Rechtsklick auslöst. */
    public boolean activeAbility() {
        return ability != null && ability.type().active();
    }
}
