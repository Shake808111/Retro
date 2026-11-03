package net.retromc.paper.core.hotbar;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Gesperrte Hotbar-Items mit Klick-Handlern (Spec 02 §4.10, §8.1 {@code HotbarLayout.slot(i, ItemStack,
 * ClickHandler)}); die Slotbelegung definiert jedes Modul.
 */
public final class HotbarLayout {

    private final Map<Integer, Slot> slots = new TreeMap<>();

    /** Klick auf ein Hotbar-Item. */
    @FunctionalInterface
    public interface ClickHandler {
        /** @param right Rechtsklick (sonst Linksklick) */
        void onClick(Player player, boolean right);
    }

    /** Ein belegter Slot. */
    public record Slot(ItemStack item, ClickHandler handler) {

        public Slot {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(handler, "handler");
        }
    }

    public static HotbarLayout create() {
        return new HotbarLayout();
    }

    /** Belegt Slot 0–8. */
    public HotbarLayout slot(int index, ItemStack item, ClickHandler handler) {
        if (index < 0 || index > 8) {
            throw new IllegalArgumentException("Hotbar-Slot 0–8: " + index);
        }
        slots.put(index, new Slot(item.clone(), handler));
        return this;
    }

    public Map<Integer, Slot> slots() {
        return Map.copyOf(slots);
    }

    /**
     * Name nach der Konvention {@code {color}{Name} §7(Rechtsklick)} (z. B. {@code §aNavigator §7(Rechtsklick)}),
     * nicht kursiv.
     */
    public static Component name(I18n i18n, Locale locale, String color, String name) {
        return Component.textOfChildren(ColorText.colored(color, name), i18n.render(locale, "item.hint.right"))
                .decoration(TextDecoration.ITALIC, false);
    }
}
