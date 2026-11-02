package net.retromc.velocity.core.bridge;

import com.velocitypowered.api.proxy.Player;

/**
 * Plugin-Message {@code <brandId>:bridge} zum Paper-Server des Spielers (Spec 01 §2.4): {@code OPEN_GUI} öffnet dort
 * eine GUI (Velocity kann keine Inventare öffnen). {@code RUN_COMMAND} vom Server führt der Proxy als Spieler aus.
 */
public interface ServerBridge {

    /**
     * Öffnet die GUI {@code guiId} auf dem aktuellen Server des Spielers.
     *
     * @param argument Argument der GUI, leer wenn keines
     * @return {@code false}, wenn der Spieler mit keinem Server verbunden ist
     */
    boolean openGui(Player player, String guiId, String argument);
}
