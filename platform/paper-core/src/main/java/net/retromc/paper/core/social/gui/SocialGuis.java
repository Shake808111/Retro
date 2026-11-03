package net.retromc.paper.core.social.gui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.Clan;
import net.retromc.core.api.social.ClanJoinMode;
import net.retromc.core.api.social.ClanMember;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.ClanService;
import net.retromc.core.api.social.FriendEntry;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyService;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.i18n.Language;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.bridge.ProxyBridge;
import net.retromc.paper.core.gui.ChestGui;
import net.retromc.paper.core.gui.ConfirmGui;
import net.retromc.paper.core.gui.GuiClick;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.paper.core.social.gui.GuiPlan.Click;
import net.retromc.paper.core.social.gui.SocialGuiPlans.ClanMemberView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.FriendView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.PartyMemberView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.Person;
import net.retromc.paper.core.social.gui.SocialGuiPlans.RequestView;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link SocialGuiService}: lädt die Daten asynchron über die Dienste der Registry, baut den Plan
 * ({@link SocialGuiPlans}), zeichnet ihn auf dem Main-Thread als {@link ChestGui} und führt Klicks aus –
 * Proxy-Befehle über {@link ProxyBridge} ({@code RUN_COMMAND}), Einstellungen direkt über {@link SettingsService}.
 */
public final class SocialGuis implements SocialGuiService {

    /** Ticks bis zum Neuzeichnen nach einem Proxy-Befehl. */
    static final long REFRESH_TICKS = 10;

    private final PaperCore core;
    private final Map<UUID, ViewState> states = new ConcurrentHashMap<>();

    public SocialGuis(PaperCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    /** Seite und Favoritenfilter je Spieler. */
    private record ViewState(String guiId, int page, boolean favoritesOnly) {
    }

    private <T> T get(Class<T> type) {
        return core.services().get(type);
    }

    /** Status beim Verlassen des Servers verwerfen. */
    public void forget(UUID player) {
        states.remove(player);
    }

    @Override
    public void open(Player player, String guiId, String argument) {
        ViewState previous = states.get(player.getUniqueId());
        ViewState state = previous != null && previous.guiId().equals(guiId) ? previous
                : new ViewState(guiId, 0, false);
        states.put(player.getUniqueId(), state);
        Locale locale = get(I18n.class).locale(player.getUniqueId());
        SocialGuiPlans.Texts texts = key -> get(I18n.class).render(locale, key);
        UUID viewer = player.getUniqueId();
        CompletableFuture<GuiPlan> plan = switch (guiId) {
            case SocialGuiPlans.PARTY -> partyView(viewer).thenApply(view -> SocialGuiPlans.party(view, state.page()));
            case SocialGuiPlans.FRIENDS -> friendsView(viewer, state.favoritesOnly())
                    .thenApply(view -> SocialGuiPlans.friends(view, state.page()));
            case SocialGuiPlans.FRIEND_OPTIONS -> friend(viewer, argument).thenApply(entry -> SocialGuiPlans
                    .friendOptions(entry.person(), entry.favorite()));
            case SocialGuiPlans.FRIEND_REMOVE -> friend(viewer, argument).thenApply(entry -> SocialGuiPlans
                    .friendRemove(entry.person()));
            case SocialGuiPlans.FRIEND_REQUESTS -> friendRequests(viewer)
                    .thenApply(list -> SocialGuiPlans.friendRequests(list, state.page()));
            case SocialGuiPlans.CLAN -> clanView(viewer).thenApply(view -> SocialGuiPlans.clan(view, texts,
                    state.page()));
            case SocialGuiPlans.CLAN_REQUESTS -> clanRequests(viewer)
                    .thenApply(list -> SocialGuiPlans.clanRequests(list, state.page()));
            case SocialGuiPlans.CLAN_SETTINGS -> clanSettings(viewer).thenApply(SocialGuiPlans::clanSettings);
            case SocialGuiPlans.CLAN_MODES -> get(ClanService.class).clanOf(viewer).thenCompose(clan -> clan.isEmpty()
                    ? CompletableFuture.completedFuture(Set.<ModeKey>of())
                    : get(ClanService.class).statsModes(clan.get().id())).thenApply(SocialGuiPlans::clanModes);
            case SocialGuiPlans.SETTINGS -> settingsView(player, locale).thenApply(view -> SocialGuiPlans.settings(view,
                    texts));
            case SocialGuiPlans.NICK -> get(SettingsService.class).settings(viewer)
                    .thenCombine(get(NickService.class).nickOf(viewer), (settings, nick) -> SocialGuiPlans.nick(
                            new SocialGuiPlans.NickView(viewer, settings.nickOnJoin(), settings.nickKeep(),
                                    nick.map(value -> value.texture()).orElse(null))));
            default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unbekannte GUI " + guiId));
        };
        core.mainThread().accept(plan, "GUI " + guiId, built -> {
            if (player.isOnline()) {
                get(GuiService.class).open(player, render(player, locale, built, guiId, argument));
            }
        });
    }

    // ------------------------------------------------------------------ Daten

    private CompletableFuture<Map<UUID, Person>> people(Collection<UUID> players) {
        return get(ProfileService.class).profiles(players).thenApply(profiles -> {
            Map<UUID, Person> result = new HashMap<>();
            for (UUID uuid : players) {
                Profile profile = profiles.get(uuid);
                result.put(uuid, profile == null ? new Person(uuid, uuid.toString().substring(0, 8), "§a")
                        : new Person(uuid, profile.name(), profile.color()));
            }
            return result;
        });
    }

    private CompletableFuture<Map<UUID, Presence>> presences(Collection<UUID> players) {
        return core.services().find(PresenceService.class).map(service -> service.presences(players))
                .orElseGet(() -> CompletableFuture.completedFuture(Map.of()));
    }

    private CompletableFuture<SocialGuiPlans.PartyView> partyView(UUID viewer) {
        CompletableFuture<Optional<Party>> party = get(PartyService.class).partyOf(viewer);
        CompletableFuture<Integer> from = get(SettingsService.class).settings(viewer).thenApply(value ->
                value.partyFrom());
        return party.thenCombine(from, PartyAndFrom::new).thenCompose(data -> {
            if (data.party().isEmpty()) {
                return CompletableFuture.completedFuture(new SocialGuiPlans.PartyView(viewer, null, 0, false, false,
                        List.of(), data.from()));
            }
            Party value = data.party().get();
            return people(value.displayOrder()).thenCombine(presences(value.memberIds()), (names, online) -> {
                List<PartyMemberView> members = value.displayOrder().stream().map(uuid -> new PartyMemberView(
                        names.get(uuid), value.isLeader(uuid), Optional.ofNullable(online.get(uuid))
                                .map(Presence::server).orElse("-"))).toList();
                return new SocialGuiPlans.PartyView(viewer, names.get(value.leader()).name(), value.maxSize(),
                        value.publicParty(), value.requireRequest(), members, data.from());
            });
        });
    }

    /** Party und {@code party_from}. */
    private record PartyAndFrom(Optional<Party> party, int from) {
    }

    private CompletableFuture<SocialGuiPlans.FriendsView> friendsView(UUID viewer, boolean favoritesOnly) {
        FriendService friends = get(FriendService.class);
        CompletableFuture<List<FriendEntry>> list = friends.list(viewer);
        CompletableFuture<Integer> requests = friends.requests(viewer).thenApply(List::size);
        CompletableFuture<RankInfo> rank = get(RankService.class).rank(viewer);
        return CompletableFuture.allOf(list, requests, rank).thenCompose(ignored -> people(list.join().stream()
                .map(FriendEntry::uuid).toList()).thenApply(names -> {
                    List<FriendView> views = list.join().stream().sorted(FriendEntry.GUI_ORDER)
                            .map(entry -> new FriendView(names.get(entry.uuid()), entry.favorite(), entry.server(),
                                    entry.lastSeen(), entry.since())).toList();
                    return new SocialGuiPlans.FriendsView(views, rank.join().limits().friends(), requests.join(),
                            favoritesOnly);
                }));
    }

    private CompletableFuture<FriendView> friend(UUID viewer, String argument) {
        UUID friend = UUID.fromString(argument);
        return get(FriendService.class).list(viewer).thenCombine(people(List.of(friend)), (list, names) -> {
            Optional<FriendEntry> entry = list.stream().filter(value -> value.uuid().equals(friend)).findFirst();
            return new FriendView(names.get(friend), entry.map(FriendEntry::favorite).orElse(false),
                    entry.map(FriendEntry::server).orElse(null), entry.map(FriendEntry::lastSeen).orElse(null),
                    entry.map(FriendEntry::since).orElse(Instant.EPOCH));
        });
    }

    private CompletableFuture<List<RequestView>> friendRequests(UUID viewer) {
        return get(FriendService.class).requests(viewer).thenCompose(requests -> people(requests.stream()
                .map(request -> request.sender()).toList()).thenApply(names -> requests.stream()
                        .map(request -> new RequestView(names.get(request.sender()), request.createdAt())).toList()));
    }

    private CompletableFuture<SocialGuiPlans.ClanView> clanView(UUID viewer) {
        ClanService clans = get(ClanService.class);
        return clans.clanOf(viewer).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(new SocialGuiPlans.ClanView(null, "", 0, 0,
                        Instant.EPOCH, ClanRole.TRIAL, 0, List.of()));
            }
            Clan clan = found.get();
            CompletableFuture<List<ClanMember>> members = clans.members(clan.id());
            CompletableFuture<Integer> max = clans.maxMembers(clan.id());
            CompletableFuture<Integer> requests = clans.requests(clan.id()).thenApply(List::size);
            return CompletableFuture.allOf(members, max, requests).thenCompose(ignored -> {
                List<UUID> ids = members.join().stream().map(ClanMember::uuid).toList();
                return people(ids).thenCombine(presences(ids), (names, online) -> {
                    List<ClanMemberView> views = new ArrayList<>(members.join().stream().map(member ->
                            new ClanMemberView(names.get(member.uuid()), member.role(),
                                    online.containsKey(member.uuid()) && online.get(member.uuid()).visible()))
                            .toList());
                    views.sort(Comparator.comparing((ClanMemberView view) -> -view.role().code())
                            .thenComparing(view -> !view.online())
                            .thenComparing(view -> view.person().name(), String.CASE_INSENSITIVE_ORDER));
                    ClanRole role = members.join().stream().filter(member -> member.uuid().equals(viewer))
                            .map(ClanMember::role).findFirst().orElse(ClanRole.TRIAL);
                    return new SocialGuiPlans.ClanView(clan.name(), clan.tag(), max.join(), clan.level().level(),
                            clan.createdAt(), role, requests.join(), views);
                });
            });
        });
    }

    private CompletableFuture<List<RequestView>> clanRequests(UUID viewer) {
        ClanService clans = get(ClanService.class);
        return clans.clanOf(viewer).thenCompose(clan -> clan.isEmpty()
                ? CompletableFuture.completedFuture(List.<RequestView>of())
                : clans.requests(clan.get().id()).thenCompose(requests -> people(requests.stream()
                        .map(request -> request.uuid()).toList()).thenApply(names -> requests.stream()
                                .map(request -> new RequestView(names.get(request.uuid()), request.createdAt()))
                                .toList())));
    }

    private CompletableFuture<SocialGuiPlans.ClanSettingsView> clanSettings(UUID viewer) {
        ClanService clans = get(ClanService.class);
        CompletableFuture<Boolean> hidden = get(SettingsService.class).settings(viewer)
                .thenApply(settings -> settings.hideFromClan());
        return clans.clanOf(viewer).thenCompose(clan -> clan.isEmpty()
                ? hidden.thenApply(hide -> new SocialGuiPlans.ClanSettingsView(ClanJoinMode.INVITE_ONLY, false,
                        Set.of(), hide))
                : clans.statsModes(clan.get().id()).thenCombine(hidden, (modes, hide) ->
                        new SocialGuiPlans.ClanSettingsView(clan.get().joinMode(), clan.get().showRoles(), modes,
                                hide)));
    }

    private CompletableFuture<SocialGuiPlans.SettingsView> settingsView(Player player, Locale locale) {
        UUID viewer = player.getUniqueId();
        return get(SettingsService.class).settings(viewer).thenCombine(people(List.of(viewer)), (settings, names) ->
                new SocialGuiPlans.SettingsView(names.get(viewer), settings, Language.of(locale).displayName()));
    }

    // ------------------------------------------------------------------ Zeichnen und Klicks

    ChestGui render(Player player, Locale locale, GuiPlan plan, String guiId, String argument) {
        I18n i18n = get(I18n.class);
        ChestGui.Builder gui = ChestGui.builder(i18n.render(locale, plan.titleKey(), plan.titleArgs()), plan.rows());
        plan.slots().forEach((index, slot) -> {
            ItemStack item = item(i18n, locale, slot);
            if (slot.click() instanceof Click.None) {
                gui.item(index, item);
            } else {
                gui.button(index, item, click -> handle(click, slot.click(), guiId, argument));
            }
        });
        return gui.build();
    }

    static ItemStack item(I18n i18n, Locale locale, GuiPlan.Slot slot) {
        Material material = Objects.requireNonNull(Material.matchMaterial(slot.material()), slot.material());
        ItemBuilder builder = ItemBuilder.of(material).texts(i18n, locale);
        if (slot.isGlass()) {
            return builder.name(Component.space()).hideTooltip().build();
        }
        if (slot.name() != null) {
            builder.name(slot.name());
        } else if (slot.nameKey() != null) {
            builder.nameKey(slot.nameKey(), slot.args());
        }
        List<Component> lore = new ArrayList<>();
        for (String key : slot.loreKeys()) {
            if (key.isEmpty()) {
                lore.add(Component.empty());
            } else {
                lore.addAll(i18n.renderLines(locale, key, slot.args()));
            }
        }
        if (!lore.isEmpty()) {
            builder.lore(lore);
        }
        if (slot.head() != null) {
            builder.skull(slot.head());
        } else if (slot.texture() != null) {
            builder.skull(slot.texture());
        }
        if (slot.amount() > 1) {
            builder.amount(slot.amount());
        }
        return builder.hideAll().build();
    }

    private void handle(GuiClick click, Click action, String guiId, String argument) {
        Player player = click.player();
        switch (action) {
            case Click.None ignored -> {
            }
            case Click.Split split -> handle(click, click.isLeft() ? split.left() : split.right(), guiId, argument);
            case Click.Commands commands -> {
                String command = commands.forClick(click.isLeft(), click.isShift());
                if (command == null) {
                    return;
                }
                if (commands.confirmKey() != null) {
                    Locale locale = get(I18n.class).locale(player.getUniqueId());
                    get(GuiService.class).open(player, ConfirmGui.builder(get(I18n.class).render(locale,
                            commands.confirmKey())).onConfirm(confirmed -> runCommand(confirmed, command, guiId,
                                    argument)).onCancel(cancelled -> open(cancelled, guiId, argument)).build());
                } else {
                    runCommand(player, command, guiId, argument);
                }
            }
            case Click.Open open -> open(player, open.guiId(), open.argument());
            case Click.Toggle toggle -> toggle(player, toggle.setting(), guiId, argument);
            case Click.Action custom -> action(player, custom.id(), guiId, argument);
            case Click.Suggest suggest -> {
                player.closeInventory();
                player.sendMessage(Component.text(suggest.command(), NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.suggestCommand(suggest.command())));
            }
        }
    }

    /** Proxy-Befehl ausführen; danach neu zeichnen, außer der Befehl verlässt die GUI-Lage. */
    private void runCommand(Player player, String command, String guiId, String argument) {
        get(ProxyBridge.class).runProxyCommand(player, command);
        if (closesGui(command)) {
            player.closeInventory();
            if (command.startsWith("friend remove ")) {
                reopenLater(player, SocialGuiPlans.FRIENDS, "");
            }
            return;
        }
        reopenLater(player, guiId, argument);
    }

    /** Befehle, nach denen die GUI geschlossen wird (Serverwechsel, Austritt, Chat-Ausgabe). */
    static boolean closesGui(String command) {
        return command.startsWith("friend jump ") || command.equals("party jump") || command.equals("party pull")
                || command.equals("party leave") || command.equals("party disband") || command.equals("clan leave")
                || command.equals("language") || command.startsWith("clan cwstats ")
                || command.startsWith("friend remove ") || command.startsWith("party invite ");
    }

    private void reopenLater(Player player, String guiId, String argument) {
        core.plugin().getServer().getScheduler().runTaskLater(core.plugin(), () -> {
            if (player.isOnline()) {
                open(player, guiId, argument);
            }
        }, REFRESH_TICKS);
    }

    private void toggle(Player player, Setting setting, String guiId, String argument) {
        UUID uuid = player.getUniqueId();
        core.mainThread().accept(get(RankService.class).rank(uuid), "Rang prüfen", rank -> {
            if (!rank.isAtLeast(setting.requiredWeight())) {
                Rank required = Rank.forWeight(setting.requiredWeight());
                player.sendMessage(get(I18n.class).render(get(I18n.class).locale(uuid), "error.rank.required",
                        Placeholder.component("rank", LegacyComponentSerializer.legacySection()
                                .deserialize(required.color()
                                        + required.displayName()))));
                return;
            }
            core.mainThread().accept(get(SettingsService.class).cycle(uuid, setting), "Einstellung ändern",
                    changed -> open(player, guiId, argument));
        });
    }

    private void action(Player player, String id, String guiId, String argument) {
        UUID uuid = player.getUniqueId();
        ViewState state = states.getOrDefault(uuid, new ViewState(guiId, 0, false));
        switch (id) {
            case "next", "page" -> {
                states.put(uuid, new ViewState(guiId, state.page() + 1, state.favoritesOnly()));
                open(player, guiId, argument);
            }
            case "prev" -> {
                states.put(uuid, new ViewState(guiId, Math.max(0, state.page() - 1), state.favoritesOnly()));
                open(player, guiId, argument);
            }
            case "favorites" -> {
                states.put(uuid, new ViewState(guiId, 0, !state.favoritesOnly()));
                open(player, guiId, argument);
            }
            case "joinmode" -> {
                ClanService clans = get(ClanService.class);
                core.mainThread().accept(clans.clanOf(uuid).thenCompose(clan -> clan.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : clans.setJoinMode(uuid, clan.get().joinMode() == ClanJoinMode.INVITE_ONLY
                                ? ClanJoinMode.INVITE_AND_REQUEST : ClanJoinMode.INVITE_ONLY)), "Beitrittsmodus",
                        result -> open(player, guiId, argument));
            }
            case "roles" -> {
                ClanService clans = get(ClanService.class);
                core.mainThread().accept(clans.clanOf(uuid).thenCompose(clan -> clan.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : clans.setShowRoles(uuid, !clan.get().showRoles())), "Ränge hervorheben",
                        result -> open(player, guiId, argument));
            }
            case "back" -> player.closeInventory();
            default -> throw new IllegalArgumentException("Unbekannte GUI-Aktion " + id);
        }
    }
}
