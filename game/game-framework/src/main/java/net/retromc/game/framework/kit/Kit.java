package net.retromc.game.framework.kit;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Start-Ausrüstung (Spec 04 §8.1 {@code game-framework.kit}, aus Spec 05 verallgemeinert): Inventar-Slots und
 * Rüstung. Modi mit Kauf/Besitz (SkyWars) bauen darauf auf; BedWars nutzt es für das Start-Inventar.
 *
 * @param id      technische ID
 * @param nameKey Textschlüssel des Namens
 * @param icon    Symbol in Menüs
 * @param slots   Inventar-Slot → Item (0–35)
 * @param armor   Rüstungsteil → Item
 */
public record Kit(String id, String nameKey, Material icon, Map<Integer, ItemStack> slots,
        Map<EquipmentSlot, ItemStack> armor) {

    public Kit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(icon, "icon");
        slots = Map.copyOf(new TreeMap<>(slots));
        armor = Map.copyOf(armor);
    }

    /** Legt die Ausrüstung an (Kopien der Items); bestehende Slots werden überschrieben. */
    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        slots.forEach((slot, item) -> inventory.setItem(slot, item.clone()));
        armor.forEach((slot, item) -> inventory.setItem(slot, item.clone()));
    }
}
