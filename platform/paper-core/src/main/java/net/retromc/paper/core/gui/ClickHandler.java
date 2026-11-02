package net.retromc.paper.core.gui;

/**
 * Aktion für einen Eintrag einer {@link PagedGui} (Spec 02 §5.3).
 *
 * @param <T> Typ der Einträge
 */
@FunctionalInterface
public interface ClickHandler<T> {

    void onClick(GuiClick click, T value);
}
