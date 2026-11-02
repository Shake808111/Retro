package net.retromc.paper.core.gui;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Basis aller GUIs (Spec 02 §5): Titel in Vanilla-Titelfarbe, 1–6 Reihen, Inhalt über {@link #draw(GuiCanvas)}.
 * Geöffnet wird über den {@link GuiService}.
 *
 * <p>Ein Objekt zeigt genau eine Ansicht für einen Spieler (Zustand wie die Seite einer Liste gehört dazu); für jeden
 * Spieler eine eigene GUI bauen. Nach dem Schließen darf sie wieder geöffnet werden (z. B. „Zurück“).
 */
public abstract class Gui {

    private final Component title;
    private final GuiGrid grid;
    private @Nullable GuiSession session;

    protected Gui(Component title, int rows) {
        this.title = Objects.requireNonNull(title, "title");
        this.grid = new GuiGrid(rows);
    }

    public final Component title() {
        return title;
    }

    public final GuiGrid grid() {
        return grid;
    }

    /** Zeichnet den Inhalt; wird beim Öffnen und nach {@link #refresh()} aufgerufen. */
    protected abstract void draw(GuiCanvas canvas);

    /** Wird aufgerufen, wenn die Ansicht geschlossen wurde (ESC, anderes Inventar, Verlassen des Servers). */
    protected void onClose(Player viewer) {
    }

    /** Zeichnet die geöffnete Ansicht neu (ohne Wirkung, wenn sie geschlossen ist). */
    public final void refresh() {
        GuiSession current = session;
        if (current != null) {
            current.redraw();
        }
    }

    /** {@code true}, solange die GUI angezeigt wird. */
    public final boolean isOpen() {
        return session != null;
    }

    final void attach(GuiSession newSession) {
        if (session != null) {
            throw new IllegalStateException("Diese GUI ist bereits geöffnet; für jeden Spieler eine eigene bauen");
        }
        session = newSession;
    }

    final boolean isShownBy(GuiSession candidate) {
        return session == candidate;
    }

    final boolean detach(GuiSession closing) {
        if (session != closing) {
            return false;
        }
        session = null;
        return true;
    }
}
