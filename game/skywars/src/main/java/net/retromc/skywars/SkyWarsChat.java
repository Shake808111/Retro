package net.retromc.skywars;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.chat.ChatFormatter;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.platform.common.text.ColorText;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Chat der SkyWars-Wartelobby (Spec 04 §7.2 {@code sw.chat.lobby}): {@code §7[{lvlColor}{lvl}§7] {rc}{name}§7: §f{msg}}
 * mit Modus-Level (ausgeblendet bei Nick) und farbigem GG/GL wie im Kern-Chat. Läuft nach dem Chat-Schutz; in der
 * Runde gilt der Rahmen ({@code GameChat}, tote Spieler {@code sw.chat.dead}).
 */
final class SkyWarsChat implements Listener {

    private final SkyWarsMode mode;

    SkyWarsChat(SkyWarsMode mode) {
        this.mode = mode;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!mode.game().lifecycle().waiting()) {
            return;
        }
        Player sender = event.getPlayer();
        UUID uuid = sender.getUniqueId();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Optional<ProfileView> view = mode.game().services().find(PlayerProfiles.class)
                .flatMap(profiles -> profiles.view(uuid));
        boolean levelVisible = view.map(ProfileView::levelVisible).orElse(true);
        boolean coloredGg = view.map(found -> found.displayRank().coloredGg()).orElse(false);
        Optional<String> gg = ChatFormatter.coloredGg(text, coloredGg, true);
        GameTexts texts = mode.texts();
        TagResolver level = mode.levelTag(uuid);
        event.renderer((source, displayName, message, viewer) -> {
            Locale locale = viewer instanceof Player player ? texts.locale(player) : Locale.GERMANY;
            Component levelPart = levelVisible ? texts.i18n().render(locale, "sw.chat.level", level)
                    : Component.empty();
            Component body = gg.isPresent() ? texts.i18n().render(locale, "chat.gg", TagResolver.resolver("gg_color",
                    Tag.styling(ChatFormatter.GG_COLOR)), Placeholder.unparsed("text", gg.get()))
                    : Component.text(text);
            return texts.i18n().render(locale, "sw.chat.lobby", Placeholder.component("level", levelPart),
                    Placeholder.unparsed("name", texts.displayName(uuid, sender.getName())),
                    ColorText.styling("name_color", texts.rankColor(uuid)), Placeholder.component("msg", body));
        });
    }
}
