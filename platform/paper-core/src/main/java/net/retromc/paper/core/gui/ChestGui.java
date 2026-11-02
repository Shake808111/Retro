package net.retromc.paper.core.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Freie Truhen-GUI aus festen Items und Knöpfen (Spec 02 §5.1, §8.1 „Builder ChestGui“). Rahmen werden zuerst
 * gezeichnet, danach Items; „Zurück“ liegt im ersten, „Schließen“ im letzten Slot der letzten Reihe (bei 6 Reihen
 * 45 und 53).
 */
public final class ChestGui extends Gui {

    /** Ein Rahmen mit Farbe. */
    private record Border(BorderShape shape, DyeColor color) {
    }

    /** Ein Item, optional mit Aktion. */
    private record Element(ItemStack item, @Nullable GuiAction action) {
    }

    private final List<Border> borders;
    private final Map<Integer, Element> elements;
    private final @Nullable GuiAction back;
    private final boolean closeButton;
    private final @Nullable Consumer<Player> onClose;

    private ChestGui(Builder builder) {
        super(builder.title, builder.grid.rows());
        this.borders = List.copyOf(builder.borders);
        this.elements = Map.copyOf(builder.elements);
        this.back = builder.back;
        this.closeButton = builder.closeButton;
        this.onClose = builder.onClose;
    }

    /** Neuer Builder; der Titel ist ein kurzer Satz ohne Farbcode (Spec 02 §5.1). */
    public static Builder builder(Component title, int rows) {
        return new Builder(title, rows);
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        for (Border border : borders) {
            canvas.border(border.shape(), GuiItems.border(border.color()));
        }
        elements.forEach((slot, element) -> {
            GuiAction action = element.action();
            if (action == null) {
                canvas.set(slot, element.item());
            } else {
                canvas.button(slot, element.item(), action);
            }
        });
        GuiGrid grid = canvas.grid();
        GuiAction backAction = back;
        if (backAction != null) {
            canvas.button(grid.slot(grid.lastRow(), 0), GuiItems.back(canvas), backAction);
        }
        if (closeButton) {
            canvas.button(grid.size() - 1, GuiItems.close(canvas), click -> click.player().closeInventory());
        }
    }

    @Override
    protected void onClose(Player viewer) {
        Consumer<Player> listener = onClose;
        if (listener != null) {
            listener.accept(viewer);
        }
    }

    /** Bauplan einer {@link ChestGui}. */
    public static final class Builder {

        private final Component title;
        private final GuiGrid grid;
        private final List<Border> borders = new ArrayList<>();
        private final Map<Integer, Element> elements = new LinkedHashMap<>();
        private @Nullable GuiAction back;
        private boolean closeButton;
        private @Nullable Consumer<Player> onClose;

        private Builder(Component title, int rows) {
            this.title = Objects.requireNonNull(title, "title");
            this.grid = new GuiGrid(rows);
        }

        /** Rahmen in der Standardfarbe (hellblau). */
        public Builder border(BorderShape shape) {
            return border(shape, GuiItems.DEFAULT_BORDER);
        }

        /** Rahmen in einer Kontextfarbe (z. B. Party violett). */
        public Builder border(BorderShape shape, DyeColor color) {
            shape.slots(grid);
            borders.add(new Border(shape, color));
            return this;
        }

        /** Item ohne Aktion. */
        public Builder item(int slot, ItemStack item) {
            elements.put(grid.requireSlot(slot), new Element(item.clone(), null));
            return this;
        }

        /** Knopf mit Aktion. */
        public Builder button(int slot, ItemStack item, GuiAction action) {
            elements.put(grid.requireSlot(slot), new Element(item.clone(), Objects.requireNonNull(action, "action")));
            return this;
        }

        /** Knopf „Zurück“ für Untermenüs. */
        public Builder back(GuiAction action) {
            this.back = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Knopf „Schließen“. */
        public Builder closeButton() {
            this.closeButton = true;
            return this;
        }

        /** Wird beim Schließen aufgerufen. */
        public Builder onClose(Consumer<Player> listener) {
            this.onClose = Objects.requireNonNull(listener, "listener");
            return this;
        }

        public ChestGui build() {
            return new ChestGui(this);
        }
    }
}
