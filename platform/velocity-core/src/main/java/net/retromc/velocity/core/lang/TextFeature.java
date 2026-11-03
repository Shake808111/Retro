package net.retromc.velocity.core.lang;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.retromc.core.api.util.Registration;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import net.retromc.platform.common.text.PlayerLocales;
import net.retromc.velocity.core.VelocityCore;

/**
 * Registriert den Standard-{@link I18n} auf dem Proxy: Katalog aus core-i18n, Sprache aus dem Profil
 * ({@link PlayerLocales}, gefüllt von {@link LanguageFeature}), sonst aus der Client-Einstellung. Ein fremdes
 * Kern-Plugin ersetzt ihn über die Service-Registry.
 */
public final class TextFeature implements CoreFeature<VelocityCore> {

    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public String name() {
        return "Texte";
    }

    @Override
    public void enable(VelocityCore core) {
        PlayerLocales locales = new PlayerLocales();
        registrations.add(core.services().registerDefault(PlayerLocales.class, locales));
        registrations.add(core.services().registerDefault(I18n.class, new CatalogI18n(locales,
                player -> core.proxy().getPlayer(player).map(Player::getEffectiveLocale))));
    }

    @Override
    public void disable(VelocityCore core) {
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
    }
}
