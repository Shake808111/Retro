package net.retromc.paper.core.gui;

import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Registriert den {@link GuiService} (Spec 02 §8.1). */
public final class GuiFeature implements CoreFeature<PaperCore> {

    private @Nullable InventoryGuiService service;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "GUIs";
    }

    @Override
    public void enable(PaperCore core) {
        InventoryGuiService guis = new InventoryGuiService(core.plugin(), core.services().supplier(I18n.class),
                core.mainThread(), core.log());
        core.plugin().getServer().getPluginManager().registerEvents(guis, core.plugin());
        service = guis;
        registration = core.services().registerDefault(GuiService.class, guis);
    }

    @Override
    public void disable(PaperCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        InventoryGuiService guis = service;
        if (guis != null) {
            HandlerList.unregisterAll(guis);
            guis.closeAll();
        }
        registration = null;
        service = null;
    }
}
