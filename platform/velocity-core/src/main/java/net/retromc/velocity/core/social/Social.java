package net.retromc.velocity.core.social;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerRegistry;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.i18n.Messages;
import net.retromc.platform.common.config.ServerNames;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.bridge.ServerBridge;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

/**
 * Gemeinsame Helfer der Social- und Moderationsbefehle auf dem Proxy: Texte (nur über {@link I18n}), Namen in
 * Rangfarbe ({@link RankService}), Spielerauflösung ({@link ProfileService}), Server-Kategorie/Modus
 * ({@link ServerRegistry} oder Servername) und Klickzeilen. Alle Dienste kommen bei jedem Gebrauch aus der
 * Service-Registry, damit Adapter sie ersetzen können.
 */
public final class Social {

    private final VelocityCore core;

    public Social(VelocityCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    public VelocityCore core() {
        return core;
    }

    public ServiceRegistry services() {
        return core.services();
    }

    public <T> T get(Class<T> type) {
        return core.services().get(type);
    }

    /** Recht {@code <brandId>.<suffix>}, z. B. {@code retromc.punish.ban}. */
    public static String permission(String suffix) {
        return BuildInfo.BRAND_ID + "." + suffix;
    }

    // ------------------------------------------------------------------ Texte

    public Locale locale(CommandSource source) {
        return source instanceof Player player ? get(I18n.class).locale(player.getUniqueId())
                : Messages.DEFAULT_LOCALE;
    }

    public Component text(CommandSource source, String key, TagResolver... placeholders) {
        return get(I18n.class).render(locale(source), key, placeholders);
    }

    public void send(CommandSource source, String key, TagResolver... placeholders) {
        source.sendMessage(text(source, key, placeholders));
    }

    /**
     * Öffnet ein Social-GUI (Spec 01 §5) über {@code OPEN_GUI} auf dem Paper-Server des Spielers; ist dort keins
     * erreichbar (kein Server, Bridge zu), kommt stattdessen der Hilfetext {@code helpKey}.
     */
    public void openGuiOrHelp(Player player, String guiId, String helpKey) {
        if (!get(ServerBridge.class).openGui(player, guiId, "")) {
            send(player, helpKey);
        }
    }

    /** Text an einen Spieler auf diesem Proxy (sonst nichts). */
    public void send(UUID player, String key, TagResolver... placeholders) {
        online(player).ifPresent(target -> send(target, key, placeholders));
    }

    /** {@code <player>} als reiner Text. */
    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }

    public static TagResolver unparsed(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Name in Rangfarbe ({@code <name>}, Spec 01 §4.4). */
    public static Component coloredName(@Nullable RankInfo rank, String name) {
        String color = rank == null ? "§a" : rank.legacyColor();
        return LegacyComponentSerializer.legacySection().deserialize(color + name);
    }

    public CompletableFuture<Component> coloredName(UUID uuid, String name) {
        return get(RankService.class).rank(uuid).thenApply(rank -> coloredName(rank, name))
                .exceptionally(error -> coloredName((RankInfo) null, name));
    }

    /**
     * Klickzeile {@code click.line} mit Knöpfen ({@code click.accept} …), jeder führt seinen Befehl aus.
     *
     * @param buttons Paare aus Knopf-Schlüssel und Befehl
     */
    public Component clickLine(CommandSource source, String prefixKey, String... buttons) {
        List<Component> rendered = new ArrayList<>();
        for (int i = 0; i + 1 < buttons.length; i += 2) {
            rendered.add(text(source, buttons[i]).clickEvent(ClickEvent.runCommand(buttons[i + 1])));
        }
        return text(source, "click.line", Placeholder.component("prefix", text(source, prefixKey)),
                Formatter.joining("buttons", rendered));
    }

    // ------------------------------------------------------------------ Spieler

    public Optional<Player> online(UUID player) {
        return core.proxy().getPlayer(player);
    }

    public Optional<Player> online(String name) {
        return core.proxy().getPlayer(name);
    }

    /** Spieler über Namen: zuerst online auf diesem Proxy, sonst über {@link ProfileService}. */
    public CompletableFuture<Optional<PlayerRef>> resolve(String name) {
        Optional<Player> local = online(name);
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(new PlayerRef(local.get().getUniqueId(),
                    local.get().getUsername())));
        }
        ProfileService profiles = get(ProfileService.class);
        return profiles.uuidOf(name).thenCompose(uuid -> uuid.isEmpty()
                ? CompletableFuture.completedFuture(Optional.<PlayerRef>empty())
                : profiles.nameOf(uuid.get()).thenApply(known -> Optional.of(new PlayerRef(uuid.get(),
                        known.orElse(name)))));
    }

    /** Zuletzt bekannter Name (online oder {@link ProfileService}), sonst die UUID. */
    public CompletableFuture<String> nameOf(UUID uuid) {
        Optional<Player> local = online(uuid);
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(local.get().getUsername());
        }
        return get(ProfileService.class).nameOf(uuid).thenApply(name -> name.orElse(uuid.toString()))
                .exceptionally(error -> uuid.toString());
    }

    /** Vorschläge: Namen der Spieler auf diesem Proxy, alphabetisch (Spec 01 A25). */
    public SuggestionProvider<CommandSource> players() {
        return SuggestionProvider.blocking((context, input) -> onlineNames().stream().map(Suggestion::suggestion)
                .toList());
    }

    public List<String> onlineNames() {
        Collection<Player> players = core.proxy().getAllPlayers();
        return players.stream().map(Player::getUsername).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public CompletableFuture<Boolean> isTeam(UUID player) {
        return get(RankService.class).rank(player).thenApply(RankInfo::isTeam).exceptionally(error -> false);
    }

    public CompletableFuture<Integer> weight(UUID player) {
        return get(RankService.class).rank(player).thenApply(RankInfo::weight).exceptionally(error -> 0);
    }

    // ------------------------------------------------------------------ Server

    public static Optional<String> serverName(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
    }

    /** Kategorie eines Servers: Server-Registry (Spec 08), sonst aus dem Namen. */
    public ServerCategory category(String server) {
        return registryInfo(server).map(ServerInfo::category).orElseGet(() -> ServerNames.categoryOfServer(server));
    }

    /** Modus eines Servers (Report-Gründe): Server-Registry, sonst aus dem Namen. */
    public String mode(String server) {
        return registryInfo(server).map(ServerInfo::mode).filter(mode -> !mode.isBlank())
                .orElseGet(() -> ServerNames.modeOfServer(server));
    }

    public Optional<ServerInfo> registryInfo(String server) {
        return services().find(ServerRegistry.class).flatMap(registry -> registry.get(server));
    }

    // ------------------------------------------------------------------ Asynchron

    /** Führt {@code action} nach Abschluss aus; Fehler → {@code error.data} und Log. */
    public <T> void then(CommandSource source, CompletableFuture<T> future, Consumer<T> action) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                core.log().error("Befehl fehlgeschlagen", error);
                send(source, "error.data");
                return;
            }
            try {
                action.accept(value);
            } catch (RuntimeException e) {
                core.log().error("Befehl fehlgeschlagen", e);
                send(source, "error.data");
            }
        });
    }

    /** Protokolliert Fehler einer Hintergrundarbeit. */
    public void log(CompletableFuture<?> future, String what) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                core.log().warn("{} fehlgeschlagen", what, error);
            }
        });
    }
}
