package net.retromc.paper.core.lang;

import java.util.Optional;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Registriert den Standard-{@link I18n} (Katalog aus core-i18n, Sprache aus der Client-Einstellung). Die Sprachwahl
 * über {@code /language} (Spec 01 §6.14) oder ein fremdes Kern-Plugin ersetzen ihn über die Service-Registry.
 */
public final class TextFeature implements CoreFeature<PaperCore> {

    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Texte";
    }

    @Override
    public void enable(PaperCore core) {
        registration = core.services().registerDefault(I18n.class, new CatalogI18n(
                player -> Optional.ofNullable(core.plugin().getServer().getPlayer(player)).map(Player::locale)));
    }

    @Override
    public void disable(PaperCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
            registration = null;
        }
    }
}
