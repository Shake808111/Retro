package net.retromc.paper.core.tab;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.retromc.core.api.util.Registration;
import org.bukkit.entity.Player;

/**
 * Tablist (Spec 02 §4.4, §6.2, §8.1 {@code TabService}): Header/Footer, Server-Footer, Einträge und Sortierung
 * ({@code setPlayerListOrder} = Rang-Gewicht, höherer Wert oben). Im Spiel liefert der Spiel-Rahmen die Einträge
 * über einen {@link EntryProvider} (Teamformat und Reihenfolge).
 */
public interface TabService {

    /** Fester Header/Footer für einen Spieler (ersetzt den Standard bis {@link #clearHeaderFooter}). */
    void setHeaderFooter(Player player, Component header, Component footer);

    void clearHeaderFooter(Player player);

    /** Server-Footer {@code §7Du bist auf Server §e{server}} mit Zusatzzeilen des Moduls je Sprache. */
    void setServerFooter(Function<Locale, List<Component>> extraLines);

    void clearServerFooter();

    /** Ersetzt {@code serverDisplay} in Header Zeile 1 (z. B. Map- oder Varianten-Name). */
    void setServerDisplay(String display);

    /** Anbieter für Einträge; der erste mit Ergebnis gilt. */
    Registration registerEntryProvider(EntryProvider provider);

    /** Zeichnet Header, Footer und Eintrag des Spielers neu. */
    void refresh(Player player);

    /** Eintrag eines Spielers: Text und Reihenfolge ({@code setPlayerListOrder}, höher = weiter oben). */
    record Entry(Component name, int order) {
    }

    /** Liefert Tab-Einträge, z. B. Teamformat {@code {teamColor}{team} §7| §f{name}} im Spiel. */
    @FunctionalInterface
    interface EntryProvider {
        Optional<Entry> entry(Player player);
    }
}
