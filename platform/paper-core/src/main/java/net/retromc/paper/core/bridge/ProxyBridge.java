package net.retromc.paper.core.bridge;

import java.util.function.BiConsumer;
import net.retromc.core.api.util.Registration;
import org.bukkit.entity.Player;

/**
 * Plugin-Message {@code <brandId>:bridge} zum Proxy (Spec 01 §2.4, §8.2): GUI-Klicks führen Proxy-Befehle als
 * Spieler aus ({@code RUN_COMMAND}); der Proxy öffnet GUIs auf dem Server ({@code OPEN_GUI}).
 */
public interface ProxyBridge {

    /**
     * Lässt den Proxy {@code commandLine} als {@code player} ausführen (z. B. {@code party promote Name}).
     *
     * @return {@code false}, wenn der Spieler nicht mehr verbunden ist
     */
    boolean runProxyCommand(Player player, String commandLine);

    /**
     * Behandelt {@code OPEN_GUI <guiId> <arg>} vom Proxy; der Handler läuft auf dem Haupt-Thread und erhält das
     * Argument (leer, wenn keines).
     *
     * @throws IllegalStateException wenn für {@code guiId} schon ein Handler registriert ist
     */
    Registration onOpenGui(String guiId, BiConsumer<Player, String> handler);
}
