package net.retromc.paper.core.gui;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/** {@link GuiService} auf InventoryFramework; Texte über den jeweils aktiven {@link I18n}-Dienst. */
public final class InventoryGuiService implements GuiService, Listener {

    private final Plugin plugin;
    private final Supplier<I18n> i18n;
    private final Executor mainThread;
    private final Logger log;
    private final ClickCooldown cooldown = new ClickCooldown();
    private final Set<GuiSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * @param i18n liefert den aktiven Textdienst (aus der Service-Registry)
     * @param mainThread führt auf dem Haupt-Thread aus
     */
    public InventoryGuiService(Plugin plugin, Supplier<I18n> i18n, Executor mainThread, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void open(Player player, Gui gui) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(gui, "gui");
        mainThread.execute(() -> {
            if (!player.isOnline()) {
                return;
            }
            GuiSession session = new GuiSession(plugin, player, gui, i18n.get(), cooldown, sessions::remove, log);
            sessions.add(session);
            try {
                session.open();
            } catch (RuntimeException e) {
                sessions.remove(session);
                throw e;
            }
        });
    }

    /** Schließt alle offenen GUIs (beim Deaktivieren des Plugins, Haupt-Thread). */
    public void closeAll() {
        for (GuiSession session : List.copyOf(sessions)) {
            session.viewer().closeInventory();
        }
        sessions.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldown.forget(event.getPlayer().getUniqueId());
    }
}
