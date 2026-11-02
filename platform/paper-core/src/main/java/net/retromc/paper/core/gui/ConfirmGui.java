package net.retromc.paper.core.gui;

import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Bestätigungsdialog (Spec 02 §5.2): Titel = Frage (höchstens 32 Zeichen), 3 Reihen, Rahmen {@code FULL_BORDER}
 * hellgrau; Slot 11 „Bestätigen“, 13 Info-Item des Aufrufers, 15 „Abbrechen“. Schließen per ESC entspricht
 * „Abbrechen“. Genau einer der beiden Rückrufe läuft, höchstens einmal.
 */
public final class ConfirmGui extends Gui {

    private final @Nullable ItemStack info;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;
    private final ConfirmState state = new ConfirmState();

    private ConfirmGui(Builder builder) {
        super(builder.question, ConfirmLayout.ROWS);
        this.info = builder.info;
        this.onConfirm = Objects.requireNonNull(builder.onConfirm, "onConfirm");
        this.onCancel = builder.onCancel;
    }

    /** Neuer Dialog mit der Frage als Titel. */
    public static Builder builder(Component question) {
        return new Builder(question);
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        canvas.border(BorderShape.FULL_BORDER, GuiItems.border(DyeColor.LIGHT_GRAY));
        canvas.button(ConfirmLayout.CONFIRM, GuiItems.confirm(canvas), click -> {
            if (state.confirm()) {
                click.player().closeInventory();
                onConfirm.accept(click.player());
            }
        });
        ItemStack infoItem = info;
        if (infoItem != null) {
            canvas.set(ConfirmLayout.INFO, infoItem);
        }
        canvas.button(ConfirmLayout.CANCEL, GuiItems.cancel(canvas), click -> {
            if (state.cancel()) {
                click.player().closeInventory();
                onCancel.accept(click.player());
            }
        });
    }

    @Override
    protected void onClose(Player viewer) {
        if (state.closed()) {
            onCancel.accept(viewer);
        }
    }

    /** Bauplan eines {@link ConfirmGui}. */
    public static final class Builder {

        private final Component question;
        private @Nullable ItemStack info;
        private @Nullable Consumer<Player> onConfirm;
        private Consumer<Player> onCancel = player -> { };

        private Builder(Component question) {
            int length = PlainTextComponentSerializer.plainText().serialize(question).length();
            if (length == 0 || length > ConfirmLayout.MAX_TITLE) {
                throw new IllegalArgumentException("Frage muss 1–32 Zeichen lang sein: " + length);
            }
            this.question = question;
        }

        /** Info-Item in Slot 13. */
        public Builder info(ItemStack item) {
            this.info = item.clone();
            return this;
        }

        /** Rückruf bei „Bestätigen“ (Pflicht); läuft nach dem Schließen des Dialogs. */
        public Builder onConfirm(Consumer<Player> action) {
            this.onConfirm = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Rückruf bei „Abbrechen“ oder ESC. */
        public Builder onCancel(Consumer<Player> action) {
            this.onCancel = Objects.requireNonNull(action, "action");
            return this;
        }

        public ConfirmGui build() {
            if (onConfirm == null) {
                throw new IllegalStateException("onConfirm fehlt");
            }
            return new ConfirmGui(this);
        }
    }
}
