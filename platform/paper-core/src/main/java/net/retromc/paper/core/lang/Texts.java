package net.retromc.paper.core.lang;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.i18n.Formats;
import net.retromc.core.i18n.Messages;
import net.retromc.platform.common.text.I18n;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Kurzwege für Befehle der Kern-UI: Sprache eines Absenders (Konsole Deutsch), Rendern und Senden über den
 * austauschbaren {@link I18n}, Zahlen nach {@code Formats} der Sprache.
 */
public final class Texts {

    private final Supplier<I18n> i18n;

    public Texts(Supplier<I18n> i18n) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
    }

    public I18n i18n() {
        return i18n.get();
    }

    public Locale locale(CommandSender sender) {
        return sender instanceof Player player ? i18n.get().locale(player.getUniqueId()) : Messages.DEFAULT_LOCALE;
    }

    public Component render(CommandSender sender, String key, TagResolver... args) {
        return i18n.get().render(locale(sender), key, args);
    }

    public void send(CommandSender sender, String key, TagResolver... args) {
        sender.sendMessage(render(sender, key, args));
    }

    /** Ganze Zahl mit Tausendertrenner der Sprache des Absenders. */
    public TagResolver number(CommandSender sender, String tag, long value) {
        return Placeholder.unparsed(tag, Formats.of(locale(sender)).integer(value));
    }
}
