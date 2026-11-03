package net.retromc.paper.core.chat;

import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Kontext einer Chatnachricht vom Spielmodul ({@link ChatContextProvider}, Spec 02 §6.1 Schritt 1–2).
 *
 * @param channel Kanal
 * @param contextPrefix Kontextpräfix des Moduls, z. B. Modus-Level {@code §7[{lvlColor}{level}§7] }, oder
 *                      {@code null}
 * @param nameColor Teamfarbe des Namens im Spiel oder {@code null} (dann Rangfarbe)
 * @param waiting Wartezeit der Runde ({@code gl} wird nur dann farbig)
 * @param message Nachricht ohne Kanalkennung (z. B. ohne {@code @all}) oder {@code null} = unverändert
 * @param recipients Empfängerfilter (Team, Zuschauer) oder {@code null} = alle
 */
public record ChatContext(ChatChannel channel, @Nullable Component contextPrefix, @Nullable TextColor nameColor,
        boolean waiting, @Nullable String message, @Nullable Predicate<Player> recipients) {

    /** Standard ohne Modul: normaler Chat an alle. */
    public static final ChatContext GLOBAL = new ChatContext(ChatChannel.GLOBAL, null, null, false, null, null);

    public ChatContext {
        Objects.requireNonNull(channel, "channel");
    }
}
