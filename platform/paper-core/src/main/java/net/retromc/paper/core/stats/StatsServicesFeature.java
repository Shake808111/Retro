package net.retromc.paper.core.stats;

import java.time.Clock;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.stats.StatsServices;
import org.jspecify.annotations.Nullable;

/**
 * Standard-Datendienste von Spec 02 ({@code StatsStore}, {@code WalletService}, {@code DisplayAnchorStore},
 * {@code NetworkLevelService}, siehe {@link StatsServices}) und der {@link ModeCatalog}. Jeder ist über die
 * Service-Registry ersetzbar.
 */
public final class StatsServicesFeature implements CoreFeature<PaperCore> {

    private @Nullable StatsServices services;
    private @Nullable Registration catalog;

    @Override
    public String name() {
        return "Stats-Dienste";
    }

    @Override
    public void enable(PaperCore core) {
        services = StatsServices.register(core.services(), Clock.systemUTC(), core.log());
        ModeCatalog modes = new ModeCatalog(core.services().supplier(StatsStore.class));
        catalog = core.services().registerDefault(ModeCatalog.class, modes);
        modes.reload().exceptionally(error -> {
            core.log().error("Modus-Katalog nicht geladen", error);
            return null;
        });
    }

    @Override
    public void disable(PaperCore core) {
        Registration current = catalog;
        if (current != null) {
            current.close();
        }
        catalog = null;
        StatsServices registered = services;
        if (registered != null) {
            registered.close();
        }
        services = null;
    }
}
