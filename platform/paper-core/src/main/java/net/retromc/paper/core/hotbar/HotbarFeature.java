package net.retromc.paper.core.hotbar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link HotbarService} (Spec 02 §4.10): markiert Items mit {@code <brand>:hotbar} (PDC), sperrt Klicks,
 * Ziehen, Ablegen und Handwechsel und ruft beim Benutzen den Handler des Slots auf.
 */
public final class HotbarFeature implements CoreFeature<PaperCore>, Listener, HotbarService {

    private final Map<UUID, HotbarLayout> layouts = new ConcurrentHashMap<>();
    private @Nullable NamespacedKey key;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Hotbar";
    }

    @Override
    public void enable(PaperCore context) {
        key = new NamespacedKey(BuildInfo.BRAND_ID, "hotbar");
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registration = context.services().registerDefault(HotbarService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        layouts.clear();
    }

    @Override
    public void apply(Player player, HotbarLayout layout) {
        clear(player);
        NamespacedKey mark = requireKey();
        layout.slots().forEach((index, slot) -> {
            ItemStack item = slot.item().clone();
            item.editPersistentDataContainer(data -> data.set(mark, PersistentDataType.INTEGER, index));
            player.getInventory().setItem(index, item);
        });
        layouts.put(player.getUniqueId(), layout);
    }

    @Override
    public void clear(Player player) {
        HotbarLayout old = layouts.remove(player.getUniqueId());
        if (old == null) {
            return;
        }
        for (int index = 0; index <= 8; index++) {
            if (slotOf(player.getInventory().getItem(index)) >= 0) {
                player.getInventory().setItem(index, null);
            }
        }
    }

    private NamespacedKey requireKey() {
        NamespacedKey current = key;
        if (current == null) {
            throw new IllegalStateException("Hotbar nicht aktiv");
        }
        return current;
    }

    /** Slot eines Hotbar-Items oder −1. */
    private int slotOf(@Nullable ItemStack item) {
        NamespacedKey current = key;
        if (item == null || current == null || item.isEmpty()) {
            return -1;
        }
        Integer slot = item.getPersistentDataContainer().get(current, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        int slot = slotOf(event.getItem());
        HotbarLayout layout = layouts.get(event.getPlayer().getUniqueId());
        if (slot < 0 || layout == null) {
            return;
        }
        event.setCancelled(true);
        HotbarLayout.Slot entry = layout.slots().get(slot);
        if (entry != null) {
            Action action = event.getAction();
            boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            entry.handler().onClick(event.getPlayer(), right);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (slotOf(event.getCurrentItem()) >= 0 || slotOf(event.getCursor()) >= 0
                || (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player
                        && slotOf(player.getInventory().getItem(event.getHotbarButton())) >= 0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (slotOf(event.getOldCursor()) >= 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (slotOf(event.getItemDrop().getItemStack()) >= 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (slotOf(event.getMainHandItem()) >= 0 || slotOf(event.getOffHandItem()) >= 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        layouts.remove(event.getPlayer().getUniqueId());
    }
}
