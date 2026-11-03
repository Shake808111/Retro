package net.retromc.paper.core.lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import net.retromc.platform.common.text.PlayerLocales;
import org.bukkit.entity.Player;

/**
 * Registriert den Standard-{@link I18n}: Katalog aus core-i18n, Sprache aus dem Profil ({@link PlayerLocales}, nach
 * dem ersten Join gilt {@code core.player.locale}, Spec 02 §6.13 Nr. 2), bis zum Laden die Client-Sprache. Ein
 * fremdes Kern-Plugin ersetzt ihn über die Service-Registry. Nach {@code /language} (Proxy) folgt
 * {@code LocaleChangedEvent}, und Tab, Scoreboard und Hologramme rendern neu.
 */
public final class TextFeature implements CoreFeature<PaperCore> {

    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public String name() {
        return "Texte";
    }

    @Override
    public void enable(PaperCore core) {
        PlayerLocales locales = new PlayerLocales();
        registrations.add(core.services().registerDefault(PlayerLocales.class, locales));
        registrations.add(core.services().registerDefault(I18n.class, new CatalogI18n(locales,
                player -> Optional.ofNullable(core.plugin().getServer().getPlayer(player)).map(Player::locale))));
    }

    @Override
    public void disable(PaperCore core) {
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
    }
}
