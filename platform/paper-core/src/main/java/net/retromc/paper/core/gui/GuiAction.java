package net.retromc.paper.core.gui;

/** Aktion eines Knopfs. */
@FunctionalInterface
public interface GuiAction {

    void onClick(GuiClick click);
}
