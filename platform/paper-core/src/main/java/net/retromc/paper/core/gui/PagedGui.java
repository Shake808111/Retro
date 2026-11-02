package net.retromc.paper.core.gui;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Listen-GUI (Spec 02 §5.3): Titel vom Aufrufer, 6 Reihen, Rahmen {@code FULL_BORDER}, 21 Einträge je Seite auf den
 * Slots aus {@link PageLayout}, Blättern nach §5.1. Leere Liste: Slot 22 „Keine Einträge“.
 *
 * @param <T> Typ der Einträge
 */
public final class PagedGui<T> extends Gui {

    private final ItemRenderer<T> renderer;
    private final ClickHandler<T> handler;
    private final DyeColor borderColor;
    private final @Nullable GuiAction back;
    private final boolean closeButton;
    private List<T> items;
    private int page;

    private PagedGui(Builder<T> builder) {
        super(builder.title, PageLayout.ROWS);
        this.items = List.copyOf(builder.items);
        this.renderer = builder.renderer;
        this.handler = builder.handler;
        this.borderColor = builder.borderColor;
        this.back = builder.back;
        this.closeButton = builder.closeButton;
        this.page = builder.page;
    }

    public static <T> Builder<T> builder(Component title, List<T> items, ItemRenderer<T> renderer,
            ClickHandler<T> handler) {
        return new Builder<>(title, items, renderer, handler);
    }

    /** Aktuelle Seite (ab 1). */
    public int page() {
        return new Pagination(items.size(), PageLayout.perPage()).clamp(page);
    }

    /** Wechselt die Seite (wird in den gültigen Bereich gezogen) und zeichnet neu. */
    public void page(int newPage) {
        this.page = new Pagination(items.size(), PageLayout.perPage()).clamp(newPage);
        refresh();
    }

    /** Ersetzt die Einträge (z. B. nach einer Änderung der Freundesliste) und zeichnet neu. */
    public void items(List<T> newItems) {
        this.items = List.copyOf(newItems);
        this.page = page();
        refresh();
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        PageView<T> view = PageView.of(items, page, PageLayout.CONTENT_SLOTS);
        page = view.page();
        canvas.border(BorderShape.FULL_BORDER, GuiItems.border(borderColor));
        if (view.empty()) {
            canvas.set(PageLayout.EMPTY, GuiItems.empty(canvas));
        }
        for (PageView.Entry<T> entry : view.entries()) {
            T value = entry.value();
            canvas.button(entry.slot(), renderer.render(value, canvas), click -> handler.onClick(click, value));
        }
        if (view.previous()) {
            canvas.button(PageLayout.PREVIOUS, GuiItems.previousPage(canvas), click -> page(view.page() - 1));
        }
        if (view.indicator()) {
            canvas.set(PageLayout.INDICATOR, GuiItems.pageIndicator(canvas, view.page(), view.pages()));
        }
        if (view.next()) {
            canvas.button(PageLayout.NEXT, GuiItems.nextPage(canvas), click -> page(view.page() + 1));
        }
        GuiAction backAction = back;
        if (backAction != null) {
            canvas.button(PageLayout.BACK, GuiItems.back(canvas), backAction);
        }
        if (closeButton) {
            canvas.button(PageLayout.CLOSE, GuiItems.close(canvas), click -> click.player().closeInventory());
        }
    }

    /**
     * Bauplan einer {@link PagedGui}.
     *
     * @param <T> Typ der Einträge
     */
    public static final class Builder<T> {

        private final Component title;
        private final List<T> items;
        private final ItemRenderer<T> renderer;
        private final ClickHandler<T> handler;
        private DyeColor borderColor = GuiItems.DEFAULT_BORDER;
        private @Nullable GuiAction back;
        private boolean closeButton;
        private int page = 1;

        private Builder(Component title, List<T> items, ItemRenderer<T> renderer, ClickHandler<T> handler) {
            this.title = Objects.requireNonNull(title, "title");
            this.items = List.copyOf(items);
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            this.handler = Objects.requireNonNull(handler, "handler");
        }

        /** Rahmenfarbe des Kontexts (Standard hellblau). */
        public Builder<T> border(DyeColor color) {
            this.borderColor = Objects.requireNonNull(color, "color");
            return this;
        }

        /** Knopf „Zurück“ (Slot 45). */
        public Builder<T> back(GuiAction action) {
            this.back = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Knopf „Schließen“ (Slot 53). */
        public Builder<T> closeButton() {
            this.closeButton = true;
            return this;
        }

        /** Startseite (ab 1). */
        public Builder<T> page(int page) {
            this.page = page;
            return this;
        }

        public PagedGui<T> build() {
            return new PagedGui<>(this);
        }
    }
}
