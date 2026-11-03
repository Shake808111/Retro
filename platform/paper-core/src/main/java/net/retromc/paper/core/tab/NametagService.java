package net.retromc.paper.core.tab;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Nametag über dem Kopf (Spec 02 §8.1 {@code NametagService}, Scoreboard-Teams): Standard ist Rang-Präfix
 * {@code {rankColor}{prefix} §7| }, Name in Rangfarbe und Clan-Tag; Spielmodule setzen Präfix/Suffix selbst (z. B.
 * Teamfarbe, Leben).
 */
public interface NametagService {

    void prefix(Player player, Component prefix);

    void suffix(Player player, Component suffix);

    /** Zurück zum Standard aus dem Profil. */
    void reset(Player player);

    /** Teamname für die Sortierung älterer Clients: {@code "%04d%s"} mit {@code 9999 − Gewicht} und Name. */
    static String teamName(int weight, String name) {
        return String.format(Locale.ROOT, "%04d%s", 9999 - Math.max(0, Math.min(9999, weight)), name);
    }
}
