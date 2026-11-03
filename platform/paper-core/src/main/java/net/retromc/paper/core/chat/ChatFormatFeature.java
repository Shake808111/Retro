package net.retromc.paper.core.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.paper.core.stats.ModeCatalog;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jspecify.annotations.Nullable;

/**
 * Chat-Format auf jedem Paper-Server (Spec 02 §6.1): rendert jede Nachricht je Empfänger mit {@link ChatFormatter}
 * (Sprache, Level-Icon nur mit Ressourcenpaket). Läuft nach dem Chat-Schutz (Priorität LOWEST, Spec 01 §6.16) und
 * übernimmt dessen bereinigten Text. Clan-Tag in Lobbys aus, auf Spielservern ohne Clan {@code §7[-] } (Option
 * {@code NETWORK_CHAT_NO_CLAN_DASH}, Standard an). Genickte Spieler: Premium, ohne Clan-Tag und Level.
 */
public final class ChatFormatFeature implements CoreFeature<PaperCore>, Listener, ChatService {

    /** Option {@code chat.noClanDash} (Spec 02 §6.1 Nr. 2). */
    static final String NO_CLAN_DASH = "NETWORK_CHAT_NO_CLAN_DASH";

    private final CopyOnWriteArrayList<ChatContextProvider> providers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChatAudienceFilter> filters = new CopyOnWriteArrayList<>();
    private @Nullable PaperCore core;
    private @Nullable Registration registration;
    private boolean noClanDash = true;

    @Override
    public String name() {
        return "Chat-Format";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        noClanDash = EnvReader.system().optional(NO_CLAN_DASH).map(Boolean::parseBoolean).orElse(true);
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registration = context.services().registerDefault(ChatService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        providers.clear();
        filters.clear();
        core = null;
    }

    @Override
    public Registration registerContext(ChatContextProvider provider) {
        providers.add(provider);
        return () -> providers.remove(provider);
    }

    @Override
    public Registration registerAudienceFilter(ChatAudienceFilter filter) {
        filters.add(filter);
        return () -> filters.remove(filter);
    }

    @Override
    public void broadcast(Audience audience, String key, TagResolver... args) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        I18n i18n = context.services().get(I18n.class);
        audience.forEachAudience(member -> member.sendMessage(i18n.render(member instanceof Player player
                ? i18n.locale(player.getUniqueId()) : Messages.DEFAULT_LOCALE, key, args)));
    }

    @Override
    public Component prefix(ModeKey mode, Locale locale) {
        PaperCore context = core;
        if (context == null) {
            return Component.empty();
        }
        I18n i18n = context.services().get(I18n.class);
        Optional<StatsMode> found = context.services().find(ModeCatalog.class).flatMap(catalog -> catalog.mode(mode));
        String color = found.map(StatsMode::color).orElse("white");
        String name = Messages.plain(locale, found.map(StatsMode::i18nKey).orElse("mode." + mode.key()), Map.of());
        return i18n.render(locale, "prefix.mode", ColorText.styling("mode_color", color), Placeholder.component(
                "mode", ColorText.name(name, color, found.map(StatsMode::gradient).orElse(null), true)));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        Player sender = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatContext chat = context(sender, plain);
        if (chat.message() != null) {
            event.message(Component.text(chat.message()));
            plain = chat.message();
        }
        event.viewers().removeIf(viewer -> viewer instanceof Player target && !target.equals(sender)
                && (filters.stream().anyMatch(filter -> filter.hides(target, sender))
                        || (chat.recipients() != null && !chat.recipients().test(target))));
        ChatFormatter.Line line = line(context, sender, chat, event.message());
        I18n i18n = context.services().get(I18n.class);
        String text = plain;
        event.renderer((source, displayName, message, viewer) -> {
            Locale locale = viewer instanceof Player player ? i18n.locale(player.getUniqueId())
                    : Messages.DEFAULT_LOCALE;
            boolean pack = viewer instanceof Player player && player.getResourcePackStatus()
                    == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
            ChatFormatter.Line rendered = new ChatFormatter.Line(line.channel(), pack ? line.level() : null,
                    line.contextPrefix(), line.clan(), line.clanTag(), line.name(), line.rank(), line.nameColor(),
                    message, line.waiting());
            return ChatFormatter.format(i18n, locale, rendered, text);
        });
    }

    private ChatContext context(Player sender, String message) {
        for (ChatContextProvider provider : providers) {
            Optional<ChatContext> found = provider.context(sender, message);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return ChatContext.GLOBAL;
    }

    /** Bausteine aus Profil und Kontext; ohne geladenes Profil Rang Spieler und echter Name. */
    private ChatFormatter.Line line(PaperCore context, Player sender, ChatContext chat, Component message) {
        UUID uuid = sender.getUniqueId();
        Optional<ProfileView> view = context.services().find(PlayerProfiles.class).flatMap(profiles ->
                profiles.view(uuid));
        RankDisplay rank = view.map(ProfileView::displayRank).orElseGet(() -> RankDisplay.of(Rank.DEFAULT));
        String name = view.map(ProfileView::displayName).orElse(sender.getName());
        @Nullable String tag = view.map(ProfileView::clanTag).orElse(null);
        Integer level = view.filter(ProfileView::levelVisible).map(ProfileView::profile).map(Profile::level)
                .filter(value -> value > 0).orElse(null);
        ChatFormatter.ClanPart clan;
        if (context.config().category() == ServerCategory.LOBBY) {
            clan = ChatFormatter.ClanPart.NONE;
        } else if (tag != null) {
            clan = ChatFormatter.ClanPart.TAG;
        } else {
            clan = noClanDash ? ChatFormatter.ClanPart.DASH : ChatFormatter.ClanPart.NONE;
        }
        return new ChatFormatter.Line(chat.channel(), level, chat.contextPrefix(), clan, tag, name, rank,
                chat.nameColor(), message, chat.waiting());
    }
}
