package net.retromc.paper.core.chat;

import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.Registration;

/**
 * Chat-Format-Engine (Spec 02 §6.1, §8.1 {@code ChatService}): Kontext und Empfängerfilter der Module, Rundsendungen
 * in der Sprache jedes Empfängers und Modus-Präfixe {@code §f[{modeColor}{Modus}§f] }. Die Durchsetzung (Mute,
 * Spam, Ignorieren) liegt davor im Chat-Schutz (Spec 01 §6.7, §6.16).
 */
public interface ChatService {

    /** Kontext-Anbieter; der erste mit Ergebnis gilt (Reihenfolge der Registrierung). */
    Registration registerContext(ChatContextProvider provider);

    Registration registerAudienceFilter(ChatAudienceFilter filter);

    /** Sendet {@code key} an jeden Empfänger in dessen Sprache. */
    void broadcast(Audience audience, String key, TagResolver... args);

    /** Modus-Präfix {@code prefix.mode}; MODERN mit Verlauf aus {@code stats.mode.gradient}. */
    Component prefix(ModeKey mode, Locale locale);
}
