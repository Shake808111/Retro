package net.retromc.game.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.i18n.Formats;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.paper.core.chat.ChatService;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Texte der Runde: Modus-Präfix {@code <prefix>} ({@code prefix.mode}, Spec 02), Ersatzschlüssel des Modus
 * ({@code common.*} → {@code bw.*}, Spec 04 §7.3), Einzahl über {@code <key>.one} und Spielernamen in Team- bzw.
 * Rangfarbe. Jeder Empfänger bekommt den Text in seiner Sprache.
 */
public final class GameTexts {

    private final ServiceRegistry services;
    private final ModeKey mode;
    private final Map<String, String> overrides;
    private final Function<UUID, @Nullable GameTeam> teamOf;
    private final boolean teamColors;

    public GameTexts(ServiceRegistry services, ModeKey mode, Map<String, String> overrides,
            Function<UUID, @Nullable GameTeam> teamOf, boolean teamColors) {
        this.services = Objects.requireNonNull(services, "services");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.overrides = Map.copyOf(overrides);
        this.teamOf = Objects.requireNonNull(teamOf, "teamOf");
        this.teamColors = teamColors;
    }

    public I18n i18n() {
        return services.get(I18n.class);
    }

    /** Schlüssel nach Ersatz durch den Modus. */
    public String key(String key) {
        return overrides.getOrDefault(key, key);
    }

    /** Einzahl-Variante {@code <key>.one}, wenn {@code n == 1} und es sie gibt. */
    public String count(String key, long n) {
        String resolved = key(key);
        return n == 1 && i18n().has(resolved + ".one") ? resolved + ".one" : resolved;
    }

    public Locale locale(CommandSender sender) {
        return sender instanceof Player player ? i18n().locale(player.getUniqueId()) : Locale.GERMANY;
    }

    /** Modus-Präfix {@code §f[{modeColor}{Modus}§f] }. */
    public Component prefix(Locale locale) {
        return services.find(ChatService.class).map(chat -> chat.prefix(mode, locale))
                .orElseGet(() -> i18n().render(locale, "prefix.mode", Placeholder.unparsed("mode", mode.key()),
                        ColorText.styling("mode_color", "§f")));
    }

    /** Rendert {@code key} (mit Ersatz) und setzt {@code <prefix>}. */
    public Component render(Locale locale, String key, TagResolver... args) {
        TagResolver[] all = Arrays.copyOf(args, args.length + 1);
        all[args.length] = Placeholder.component("prefix", prefix(locale));
        return i18n().render(locale, key(key), all);
    }

    public Component render(CommandSender viewer, String key, TagResolver... args) {
        return render(locale(viewer), key, args);
    }

    public void send(CommandSender viewer, String key, TagResolver... args) {
        viewer.sendMessage(render(viewer, key, args));
    }

    /** An alle Spieler des Servers. */
    public void broadcast(String key, TagResolver... args) {
        broadcast(new ArrayList<>(Bukkit.getOnlinePlayers()), key, args);
    }

    public void broadcast(Iterable<? extends Player> receivers, String key, TagResolver... args) {
        for (Player receiver : receivers) {
            send(receiver, key, args);
        }
    }

    /** Ganze Zahl mit Tausendertrenner der Sprache. */
    public TagResolver number(Locale locale, String tag, long value) {
        return Placeholder.unparsed(tag, Formats.of(locale).integer(value));
    }

    // ------------------------------------------------------------------------------------------ Namen

    /** Anzeigename (Nick beachtet). */
    public String displayName(UUID player, String fallback) {
        return view(player).map(ProfileView::displayName).orElse(fallback);
    }

    /** Farbe des Namens: Teamfarbe in Teammodi, sonst Rangfarbe (Spec 03 §6.9). */
    public String nameColor(UUID player) {
        GameTeam team = teamOf.apply(player);
        if (teamColors && team != null) {
            return team.colorCode();
        }
        return rankColor(player);
    }

    /** Rangfarbe ({@code §a} ohne Profil). */
    public String rankColor(UUID player) {
        return view(player).map(ProfileView::displayRank).map(RankDisplay::color).orElse(Rank.DEFAULT.color());
    }

    /** Platzhalter {@code <tag>} (Name) und {@code <tag_color>} (Farbe) für einen Spieler. */
    public TagResolver name(String tag, UUID player, String fallbackName) {
        return TagResolver.resolver(Placeholder.unparsed(tag, displayName(player, fallbackName)),
                ColorText.styling(tag + "_color", nameColor(player)));
    }

    public TagResolver name(String tag, Player player) {
        return name(tag, player.getUniqueId(), player.getName());
    }

    /** Platzhalter {@code <team>} (Name in der Sprache) und {@code <team_color>}. */
    public TagResolver team(Locale locale, GameTeam team) {
        return TagResolver.resolver(Placeholder.component("team", teamName(locale, team)),
                ColorText.styling("team_color", team.colorCode()));
    }

    /** Teamname ohne Farbe ({@code Rot}, {@code #4}). */
    public Component teamName(Locale locale, GameTeam team) {
        return i18n().render(locale, team.nameKey(), Placeholder.unparsed("n", Integer.toString(team.index() + 1)));
    }

    /** Clan-Tag {@code §7[§e{tag}§7] } oder leer (für Chat-Formate mit {@code <clan>}). */
    public Component clan(Locale locale, UUID player) {
        @Nullable String tag = view(player).map(ProfileView::clanTag).orElse(null);
        return tag == null ? Component.empty()
                : i18n().render(locale, "chat.clan-tag", Placeholder.unparsed("tag", tag));
    }

    /** Ränge (Gewicht) für Rechteprüfungen ohne Datenbankzugriff. */
    public int rankWeight(UUID player) {
        return view(player).map(view -> view.rank().weight()).orElse(0);
    }

    private Optional<ProfileView> view(UUID player) {
        return services.find(PlayerProfiles.class).flatMap(profiles -> profiles.view(player));
    }

    /** Alle Zeilen eines mehrzeiligen Schlüssels. */
    public List<Component> lines(Locale locale, String key, TagResolver... args) {
        return i18n().renderLines(locale, key(key), args);
    }
}
