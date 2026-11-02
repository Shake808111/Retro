package net.retromc.paper.core.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Eine angezeigte {@link Gui} für einen Spieler, gezeichnet mit InventoryFramework (Spec 02 §5.1).
 *
 * <ul>
 *   <li>Jeder Klick und jedes Ziehen wird abgebrochen: Items lassen sich weder entnehmen noch ablegen, auch nicht
 *       per Umschalt-Klick; das Spielerinventar unten ist gesperrt.</li>
 *   <li>Nur Knöpfe im oberen Inventar lösen aus, höchstens alle 150 ms je Spieler ({@link ClickCooldown}).</li>
 *   <li>Aktionen laufen einen Tick später, damit sie Inventare öffnen und schließen dürfen (das ist im Klick-Event
 *       selbst nicht erlaubt), mit Klickton; Fehler werden protokolliert und mit dem Fehlerton quittiert.</li>
 * </ul>
 */
final class GuiSession {

    private final Plugin plugin;
    private final Player viewer;
    private final Gui gui;
    private final I18n i18n;
    private final Locale locale;
    private final ClickCooldown cooldown;
    private final Consumer<GuiSession> onEnd;
    private final Logger log;
    private final com.github.stefvanschie.inventoryframework.gui.type.ChestGui view;
    private final StaticPane pane;

    GuiSession(Plugin plugin, Player viewer, Gui gui, I18n i18n, ClickCooldown cooldown, Consumer<GuiSession> onEnd,
            Logger log) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.gui = gui;
        this.i18n = i18n;
        this.locale = i18n.locale(viewer.getUniqueId());
        this.cooldown = cooldown;
        this.onEnd = onEnd;
        this.log = log;
        this.view = new com.github.stefvanschie.inventoryframework.gui.type.ChestGui(gui.grid().rows(),
                ComponentHolder.of(gui.title()), plugin);
        this.pane = new StaticPane(GuiGrid.COLUMNS, gui.grid().rows());
        view.addPane(Slot.fromIndex(0), pane);
        view.setOnGlobalClick(event -> event.setCancelled(true));
        view.setOnGlobalDrag(event -> event.setCancelled(true));
        view.setOnClose(event -> closed());
    }

    Player viewer() {
        return viewer;
    }

    /** Zeichnet und zeigt die GUI (Haupt-Thread). */
    void open() {
        gui.attach(this);
        try {
            draw();
            view.show(viewer);
        } catch (RuntimeException e) {
            gui.detach(this);
            throw e;
        }
    }

    /** Zeichnet neu, solange die GUI angezeigt wird. */
    void redraw() {
        if (gui.isShownBy(this)) {
            draw();
            view.update();
        }
    }

    private void draw() {
        Canvas canvas = new Canvas();
        gui.draw(canvas);
        pane.clear();
        canvas.items.forEach((slot, item) -> pane.addItem(item, Slot.fromIndex(slot)));
    }

    private void closed() {
        if (!gui.detach(this)) {
            return;
        }
        onEnd.accept(this);
        try {
            gui.onClose(viewer);
        } catch (RuntimeException e) {
            log.error("Fehler beim Schließen der GUI für {}", viewer.getName(), e);
        }
    }

    private void clicked(InventoryClickEvent event, int slot, GuiAction action) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || !cooldown.tryClick(viewer.getUniqueId())) {
            return;
        }
        ClickType type = event.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> run(action, new GuiClick(viewer, type, slot)));
    }

    private void run(GuiAction action, GuiClick click) {
        if (!gui.isShownBy(this)) {
            return;
        }
        GuiSounds.click(viewer);
        try {
            action.onClick(click);
        } catch (RuntimeException e) {
            log.error("GUI-Aktion in Slot {} für {} fehlgeschlagen", click.slot(), viewer.getName(), e);
            GuiSounds.error(viewer);
        }
    }

    /** Sammelt die Items einer Zeichnung; spätere Einträge für denselben Slot gewinnen. */
    private final class Canvas implements GuiCanvas {

        private final Map<Integer, GuiItem> items = new LinkedHashMap<>();

        @Override
        public Player viewer() {
            return viewer;
        }

        @Override
        public Locale locale() {
            return locale;
        }

        @Override
        public I18n i18n() {
            return i18n;
        }

        @Override
        public GuiGrid grid() {
            return gui.grid();
        }

        @Override
        public void set(int slot, ItemStack item) {
            items.put(gui.grid().requireSlot(slot), new GuiItem(item.clone(), event -> event.setCancelled(true),
                    plugin));
        }

        @Override
        public void button(int slot, ItemStack item, GuiAction action) {
            Objects.requireNonNull(action, "action");
            items.put(gui.grid().requireSlot(slot), new GuiItem(item.clone(), event -> clicked(event, slot, action),
                    plugin));
        }
    }
}
