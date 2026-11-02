package net.retromc.velocity.core.lang;

import com.velocitypowered.api.proxy.Player;
import net.retromc.core.api.util.Registration;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import org.jspecify.annotations.Nullable;

/**
 * Registriert den Standard-{@link I18n} auf dem Proxy (Katalog aus core-i18n, Sprache aus der Client-Einstellung).
 * Die Sprachwahl über {@code /language} (Spec 01 §6.14) oder ein fremdes Kern-Plugin ersetzen ihn über die
 * Service-Registry.
 */
public final class TextFeature implements CoreFeature<VelocityCore> {

    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Texte";
    }

    @Override
    public void enable(VelocityCore core) {
        registration = core.services().registerDefault(I18n.class, new CatalogI18n(
                player -> core.proxy().getPlayer(player).map(Player::getEffectiveLocale)));
    }

    @Override
    public void disable(VelocityCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
            registration = null;
        }
    }
}
