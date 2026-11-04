package net.retromc.paper.core.social.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.retromc.core.api.social.ClanJoinMode;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.paper.core.social.gui.GuiPlan.Click;
import net.retromc.paper.core.social.gui.GuiPlan.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Baupläne der Social-GUIs nach Spec 01 §5 (5.1 Partymenü, 5.2 Freunde, 5.2a Freund-Optionen, 5.3
 * Freundschaftsanfragen, 5.4 Clan, 5.5 Clan-Einstellungen, 5.6 Einstellungen, 5.7 Nicknamen). Reine Funktionen über
 * Datensätzen; Klicks auf Proxy-Funktionen sind {@code RUN_COMMAND}-Befehle ohne {@code /} (A24).
 */
public final class SocialGuiPlans {

    /** GUI-IDs für {@code OPEN_GUI} und {@link SocialGuiService}. */
    public static final String PARTY = "party";
    public static final String FRIENDS = "friends";
    public static final String FRIEND_OPTIONS = "friends.options";
    public static final String FRIEND_REMOVE = "friends.remove";
    public static final String FRIEND_REQUESTS = "friends.requests";
    public static final String CLAN = "clan";
    public static final String CLAN_SETTINGS = "clan.settings";
    public static final String CLAN_MODES = "clan.modes";
    public static final String CLAN_REQUESTS = "clan.requests";
    public static final String SETTINGS = "settings";
    public static final String NICK = "nick";

    /** Leere Lore-Zeile. */
    public static final String BLANK = "";

    public static final String PURPLE_GLASS = "PURPLE_STAINED_GLASS_PANE";
    public static final String RED_GLASS = "RED_STAINED_GLASS_PANE";
    public static final String YELLOW_GLASS = "YELLOW_STAINED_GLASS_PANE";
    public static final String LIGHT_BLUE_GLASS = "LIGHT_BLUE_STAINED_GLASS_PANE";

    /** Köpfe je Seite: Partymenü 9–26, Freunde und Anfragen 0–44, Clan 9–44. */
    public static final int PARTY_HEADS = 18;
    public static final int LIST_HEADS = 45;
    public static final int CLAN_HEADS = 36;

    /** Modi der Clan-Statistik (Unter-GUI §5.5). */
    public static final List<ModeKey> CLAN_STAT_MODES = List.of(ModeKey.BEDWARS, ModeKey.SKYWARS,
            ModeKey.SURVIVALGAMES, ModeKey.CORES, ModeKey.TTT, ModeKey.ENDERGAMES, ModeKey.GUNGAME,
            ModeKey.MASTERBUILDERS);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.of("Europe/Berlin"));

    private SocialGuiPlans() {
    }

    /** Texte, die ein Plan selbst einsetzt (Rollen, Werte); auf Paper der {@code I18n} der Sprache des Betrachters. */
    @FunctionalInterface
    public interface Texts {
        Component render(String key);
    }

    /** Spieler mit Rangfarbe ({@code §a}) für Köpfe und Namen. */
    public record Person(UUID uuid, String name, String color) {

        public Component colored() {
            return LegacyComponentSerializer.legacySection().deserialize(color + name);
        }

        TagResolver nameArg() {
            return Placeholder.component("name", colored());
        }
    }

    static String date(Instant instant) {
        return DATE.format(instant);
    }

    private static Slot head(Person person, Component name, List<String> lore, TagResolver args, Click click) {
        return new Slot("PLAYER_HEAD", null, name, lore, args, person.uuid(), null, 1, click);
    }

    // ------------------------------------------------------------------ 5.1 Partymenü

    /** Mitglied im Partymenü. */
    public record PartyMemberView(Person person, boolean leader, String server) {
    }

    /**
     * Daten des Partymenüs.
     *
     * @param members Leiter zuerst, dann Beitrittsreihenfolge; leer = keine Party
     * @param partyFrom {@code party_from} des Betrachters (2/1/0)
     */
    public record PartyView(UUID viewer, @Nullable String leaderName, int max, boolean publicParty,
            boolean requireRequest, List<PartyMemberView> members, int partyFrom) {

        public PartyView {
            members = List.copyOf(members);
        }

        boolean viewerLeads() {
            return members.stream().anyMatch(member -> member.leader() && member.person().uuid().equals(viewer));
        }
    }

    public static GuiPlan party(PartyView view, int page) {
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.party.title", TagResolver.empty(), 5);
        plan.glass(PURPLE_GLASS, 0, 1, 2, 3, 5, 6, 7, 8).glassRange(PURPLE_GLASS, 27, 35);
        if (view.members().isEmpty()) {
            plan.item(22, "CAKE", "gui.party.create", List.of("gui.party.create.lore"), TagResolver.empty(),
                    new Click.Suggest("/party invite "));
            return plan.build();
        }
        boolean leads = view.viewerLeads();
        plan.item(4, "CAKE", "gui.party.info", List.of("gui.party.info.lore"), TagResolver.resolver(
                Placeholder.unparsed("leader", view.leaderName() == null ? "" : view.leaderName()),
                Placeholder.unparsed("n", Integer.toString(view.members().size())),
                Placeholder.unparsed("max", Integer.toString(view.max())),
                Formatter.booleanChoice("public", view.publicParty())), Click.NONE);
        // Blättern über Slot 40 läuft im Kreis (nach der letzten Seite wieder die erste).
        int partyPage = Math.floorMod(page, pages(view.members().size(), PARTY_HEADS));
        List<PartyMemberView> shown = view.members().stream().skip((long) partyPage * PARTY_HEADS)
                .limit(PARTY_HEADS).toList();
        for (int i = 0; i < shown.size(); i++) {
            PartyMemberView member = shown.get(i);
            boolean manageable = leads && !member.leader();
            String name = member.person().name();
            plan.slot(9 + i, head(member.person(), member.person().colored(), manageable
                    ? List.of("gui.party.member.lore", BLANK, "gui.party.member.manage")
                    : List.of("gui.party.member.lore"), TagResolver.resolver(
                            Formatter.booleanChoice("leader", member.leader()),
                            Placeholder.unparsed("server", member.server())),
                    manageable ? new Click.Commands("party promote " + name, "party kick " + name,
                            "party kick " + name, "gui.party.confirm.kick") : Click.NONE));
        }
        plan.item(36, "GOLDEN_HELMET", "gui.party.public", List.of("gui.party.public.lore"),
                Formatter.booleanChoice("enabled", view.publicParty()), Click.Commands.any("party public"));
        plan.item(37, "IRON_CHESTPLATE", "gui.party.requests", List.of("gui.party.requests.lore"),
                Formatter.booleanChoice("enabled", view.requireRequest()),
                Click.Commands.any("party requests " + (view.requireRequest() ? "aus" : "an")));
        plan.item(38, "FIREWORK_ROCKET", "gui.party.jump", List.of(), TagResolver.empty(),
                Click.Commands.any("party jump"));
        plan.item(39, "LEAD", "gui.party.pull", List.of("gui.party.pull.lore"), TagResolver.empty(),
                Click.Commands.any("party pull"));
        if (view.members().size() > PARTY_HEADS) {
            plan.item(40, "ARROW", "gui.social.next", List.of(), TagResolver.empty(), new Click.Action("page"));
        }
        plan.item(41, "BLAZE_ROD", "gui.party.invite", List.of(), TagResolver.empty(),
                new Click.Suggest("/party invite "));
        plan.item(43, "PURPLE_CONCRETE", "gui.party.from", List.of(switch (view.partyFrom()) {
            case 2 -> "gui.party.from.all";
            case 1 -> "gui.party.from.friends";
            default -> "gui.party.from.off";
        }), TagResolver.empty(), Click.Commands.any("partytoggle"));
        plan.item(44, "PURPLE_CONCRETE", leads ? "gui.party.disband" : "gui.party.leave", List.of(),
                TagResolver.empty(), leads ? Click.Commands.confirmed("party disband", "gui.party.confirm.disband")
                        : Click.Commands.confirmed("party leave", "gui.party.confirm.leave"));
        return plan.build();
    }

    // ------------------------------------------------------------------ 5.2 Freunde, 5.2a Optionen

    /** Freund in der GUI. */
    public record FriendView(Person person, boolean favorite, @Nullable String server, @Nullable Instant lastSeen,
            Instant since) {

        boolean online() {
            return server != null;
        }
    }

    /**
     * Daten der Freunde-GUI.
     *
     * @param friends bereits sortiert: online Favoriten, online, offline Favoriten, offline (je alphabetisch)
     */
    public record FriendsView(List<FriendView> friends, int max, int openRequests, boolean favoritesOnly) {

        public FriendsView {
            friends = List.copyOf(friends);
        }
    }

    public static GuiPlan friends(FriendsView view, int page) {
        List<FriendView> visible = view.friends().stream().filter(friend -> !view.favoritesOnly()
                || friend.favorite()).toList();
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.friends.title", TagResolver.resolver(
                Placeholder.unparsed("n", Integer.toString(view.friends().size())),
                Placeholder.unparsed("max", Integer.toString(view.max()))), 6);
        int lastPage = pages(visible.size(), LIST_HEADS) - 1;
        int friendsPage = Math.clamp(page, 0, lastPage);
        List<FriendView> shown = visible.stream().skip((long) friendsPage * LIST_HEADS).limit(LIST_HEADS).toList();
        for (int i = 0; i < shown.size(); i++) {
            FriendView friend = shown.get(i);
            Component name = friend.favorite()
                    ? friend.person().colored().append(Component.text(" ✦", NamedTextColor.YELLOW))
                    : friend.person().colored();
            TagResolver args = TagResolver.resolver(
                    Placeholder.unparsed("server", friend.server() == null ? "" : friend.server()),
                    Placeholder.unparsed("last", friend.lastSeen() == null ? "-" : date(friend.lastSeen())),
                    Placeholder.unparsed("since", date(friend.since())));
            plan.slot(i, head(friend.person(), name, List.of(friend.online() ? "gui.friends.online.lore"
                    : "gui.friends.offline.lore", "gui.friends.since", BLANK, "gui.friends.clicks"), args,
                    new Click.Split(Click.Commands.any("friend jump " + friend.person().name()),
                            new Click.Open(FRIEND_OPTIONS, friend.person().uuid().toString()))));
        }
        plan.glass(RED_GLASS, 46, 48, 50, 52);
        // Pfeile nur bei Bedarf, sonst Rahmen (Spec 02 §5.1 „Blättern“).
        if (friendsPage > 0) {
            plan.item(45, "ARROW", "gui.social.prev", List.of(), TagResolver.empty(), new Click.Action("prev"));
        } else {
            plan.glass(RED_GLASS, 45);
        }
        plan.slot(47, new Slot("BOOK", "gui.friends.requests", null, List.of("gui.friends.requests.lore"),
                Placeholder.unparsed("n", Integer.toString(view.openRequests())), null, null,
                Math.max(1, Math.min(64, view.openRequests())), new Click.Open(FRIEND_REQUESTS, "")));
        plan.item(49, "COMPARATOR", "gui.friends.settings", List.of(), TagResolver.empty(),
                new Click.Open(SETTINGS, ""));
        plan.item(51, "NETHER_STAR", "gui.friends.favorites", List.of("gui.status.lore"),
                Formatter.booleanChoice("enabled", view.favoritesOnly()), new Click.Action("favorites"));
        if (friendsPage < lastPage) {
            plan.item(53, "ARROW", "gui.social.next", List.of(), TagResolver.empty(), new Click.Action("next"));
        } else {
            plan.glass(RED_GLASS, 53);
        }
        return plan.build();
    }

    /** Seitenzahl einer Liste, mindestens 1. */
    static int pages(int entries, int perPage) {
        return Math.max(1, (entries + perPage - 1) / perPage);
    }

    public static GuiPlan friendOptions(Person friend, boolean favorite) {
        String name = friend.name();
        return new GuiPlan.Builder("gui.friends.opt.title", friend.nameArg(), 3)
                .item(10, "ENDER_PEARL", "gui.friends.opt.jump", List.of(), TagResolver.empty(),
                        Click.Commands.any("friend jump " + name))
                .item(12, "CAKE", "gui.friends.opt.party", List.of(), TagResolver.empty(),
                        Click.Commands.any("party invite " + name))
                .item(14, "NETHER_STAR", "gui.friends.opt.favorite", List.of("gui.status.lore"),
                        Formatter.booleanChoice("enabled", favorite), Click.Commands.any("friend favorite " + name))
                .item(16, "BARRIER", "gui.friends.opt.remove", List.of(), TagResolver.empty(),
                        new Click.Open(FRIEND_REMOVE, friend.uuid().toString()))
                .item(18, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Open(FRIENDS, ""))
                .build();
    }

    public static GuiPlan friendRemove(Person friend) {
        return new GuiPlan.Builder("gui.friends.remove.title", friend.nameArg(), 3)
                .item(11, "LIME_CONCRETE", "gui.social.yes", List.of(), TagResolver.empty(),
                        Click.Commands.any("friend remove " + friend.name()))
                .item(15, "RED_CONCRETE", "gui.social.no", List.of(), TagResolver.empty(),
                        new Click.Open(FRIEND_OPTIONS, friend.uuid().toString()))
                .build();
    }

    // ------------------------------------------------------------------ 5.3 Anfragen (Freunde, Clan)

    /** Offene Anfrage. */
    public record RequestView(Person person, Instant sent) {
    }

    public static GuiPlan friendRequests(List<RequestView> requests, int page) {
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.requests.title", TagResolver.empty(), 6);
        requestHeads(plan, requests, page, "friend");
        plan.item(45, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Open(FRIENDS, ""));
        plan.item(48, "LIME_CONCRETE", "gui.requests.acceptall", List.of(), TagResolver.empty(),
                Click.Commands.any("friend acceptall"));
        plan.item(50, "RED_CONCRETE", "gui.requests.denyall", List.of(), TagResolver.empty(),
                Click.Commands.any("friend denyall"));
        return plan.build();
    }

    public static GuiPlan clanRequests(List<RequestView> requests, int page) {
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.clan.requests.title", TagResolver.empty(), 6);
        requestHeads(plan, requests, page, "clan");
        plan.item(45, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Open(CLAN, ""));
        return plan.build();
    }

    private static void requestHeads(GuiPlan.Builder plan, List<RequestView> requests, int page, String command) {
        List<RequestView> shown = requests.stream().skip((long) page * LIST_HEADS).limit(LIST_HEADS).toList();
        for (int i = 0; i < shown.size(); i++) {
            RequestView request = shown.get(i);
            String name = request.person().name();
            plan.slot(i, head(request.person(), request.person().colored(), List.of("gui.requests.lore"),
                    Placeholder.unparsed("date", date(request.sent())),
                    new Click.Commands(command + " accept " + name, command + " deny " + name, null, null)));
        }
    }

    // ------------------------------------------------------------------ 5.4 Clan, 5.5 Einstellungen

    /** Mitglied in der Clan-GUI. */
    public record ClanMemberView(Person person, ClanRole role, boolean online) {
    }

    /**
     * Daten der Clan-GUI; {@code name == null} = kein Clan.
     *
     * @param members nach Rolle (Leiter, Moderatoren, Mitglieder, Testmitglieder), online zuerst
     */
    public record ClanView(@Nullable String name, String tag, int max, int level, Instant founded,
            ClanRole viewerRole, int openRequests, List<ClanMemberView> members) {

        public ClanView {
            members = List.copyOf(members);
        }
    }

    public static GuiPlan clan(ClanView view, Texts texts, int page) {
        if (view.name() == null) {
            return new GuiPlan.Builder("gui.clan.title.none", TagResolver.empty(), 6)
                    .glass(YELLOW_GLASS, 0, 1, 2, 3, 4, 5, 6, 7, 8).glassRange(YELLOW_GLASS, 45, 53)
                    .item(22, "WHITE_BANNER", "gui.clan.create", List.of("gui.clan.create.lore"),
                            TagResolver.empty(), new Click.Suggest("/clan create "))
                    .build();
        }
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.clan.title", Placeholder.unparsed("tag", view.tag()), 6);
        plan.glass(YELLOW_GLASS, 0, 1, 2, 3, 5, 6, 7, 8, 46, 48, 50, 52);
        plan.item(4, "WHITE_BANNER", "gui.clan.info", List.of("gui.clan.info.lore"), TagResolver.resolver(
                Placeholder.unparsed("clan", view.name()), Placeholder.unparsed("tag", view.tag()),
                Placeholder.unparsed("n", Integer.toString(view.members().size())),
                Placeholder.unparsed("max", Integer.toString(view.max())),
                Placeholder.unparsed("lvl", Integer.toString(view.level())),
                Placeholder.unparsed("date", date(view.founded()))), Click.NONE);
        boolean lead = view.viewerRole().canInvite();
        List<ClanMemberView> shown = view.members().stream().skip((long) page * CLAN_HEADS).limit(CLAN_HEADS)
                .toList();
        for (int i = 0; i < shown.size(); i++) {
            ClanMemberView member = shown.get(i);
            String name = member.person().name();
            TagResolver args = TagResolver.resolver(Placeholder.component("role", texts.render(member.role()
                    .i18nKey())), Formatter.booleanChoice("online", member.online()),
                    Placeholder.component("cw", texts.render(member.role() == ClanRole.TRIAL ? "gui.clan.cw.trial"
                            : "gui.clan.cw.ready")));
            plan.slot(9 + i, head(member.person(), member.person().colored(), lead
                    ? List.of("gui.clan.member.lore", "gui.clan.member.manage") : List.of("gui.clan.member.lore"),
                    args, lead ? new Click.Commands("clan promote " + name, "clan demote " + name,
                            "clan kick " + name, null) : Click.NONE));
        }
        plan.item(45, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Action("back"));
        plan.item(47, "IRON_SWORD", "gui.clan.cwstats", List.of(), TagResolver.empty(),
                Click.Commands.any("clan cwstats " + view.name()));
        plan.item(49, "COMPARATOR", "gui.clan.settings", List.of(), TagResolver.empty(),
                new Click.Open(CLAN_SETTINGS, ""));
        plan.item(51, "PAPER", "gui.clan.requests", List.of("gui.clan.requests.lore"),
                Placeholder.unparsed("n", Integer.toString(view.openRequests())), new Click.Open(CLAN_REQUESTS, ""));
        plan.item(53, "RED_CONCRETE", "gui.clan.leave", List.of(), TagResolver.empty(),
                Click.Commands.confirmed("clan leave", "gui.clan.confirm.leave"));
        return plan.build();
    }

    /** Daten der Clan-Einstellungen. */
    public record ClanSettingsView(ClanJoinMode joinMode, boolean showRoles, Set<ModeKey> statsModes,
            boolean hideFromClan) {

        public ClanSettingsView {
            statsModes = Set.copyOf(statsModes);
        }
    }

    public static GuiPlan clanSettings(ClanSettingsView view) {
        boolean requests = view.joinMode() == ClanJoinMode.INVITE_AND_REQUEST;
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.clan.set.title", TagResolver.empty(), 4);
        plan.glassRange(LIGHT_BLUE_GLASS, 0, 8).glassRange(LIGHT_BLUE_GLASS, 28, 34);
        Click join = new Click.Action("joinmode");
        Click roles = new Click.Action("roles");
        Click modes = new Click.Open(CLAN_MODES, "");
        Click hide = new Click.Toggle(Setting.HIDE_FROM_CLAN);
        plan.item(11, "OAK_DOOR", "gui.clan.set.join", List.of("gui.clan.set.join.lore"),
                Formatter.booleanChoice("requests", requests), join);
        plan.item(13, "NAME_TAG", "gui.clan.set.roles", List.of("gui.clan.set.roles.lore"), TagResolver.empty(),
                roles);
        plan.item(15, "DIAMOND_SWORD", "gui.clan.set.modes", List.of("gui.clan.set.modes.lore"), TagResolver.empty(),
                modes);
        plan.slot(20, requests ? status("LIME_DYE", "gui.status.on", join)
                : status("ORANGE_DYE", "gui.clan.set.join.limited", join));
        plan.slot(22, view.showRoles() ? status("LIME_DYE", "gui.status.on", roles)
                : status("GRAY_DYE", "gui.status.off", roles));
        plan.slot(24, view.statsModes().isEmpty() ? status("GRAY_DYE", "gui.status.off", modes)
                : new Slot("LIME_DYE", "gui.status.count", null, List.of(),
                        Placeholder.unparsed("n", Integer.toString(view.statsModes().size())), null, null,
                        view.statsModes().size(), modes));
        plan.item(26, "ENDER_EYE", "gui.clan.set.hide", List.of("gui.clan.set.hide.lore"), TagResolver.empty(), hide);
        plan.slot(35, view.hideFromClan() ? status("LIME_DYE", "gui.status.on", hide)
                : status("GRAY_DYE", "gui.status.off", hide));
        plan.item(27, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Open(CLAN, ""));
        return plan.build();
    }

    public static GuiPlan clanModes(Set<ModeKey> enabled) {
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.clan.modes.title", TagResolver.empty(), 4);
        plan.glassRange(LIGHT_BLUE_GLASS, 0, 8).glassRange(LIGHT_BLUE_GLASS, 28, 35);
        for (int i = 0; i < CLAN_STAT_MODES.size(); i++) {
            ModeKey mode = CLAN_STAT_MODES.get(i);
            Click toggle = Click.Commands.any("clan togglestats " + mode.key());
            plan.item(9 + i, modeIcon(mode), "gui.clan.mode", List.of(), Placeholder.unparsed("mode", mode.key()),
                    toggle);
            plan.slot(18 + i, enabled.contains(mode) ? status("LIME_DYE", "gui.status.on", toggle)
                    : status("GRAY_DYE", "gui.status.off", toggle));
        }
        plan.item(27, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Open(CLAN_SETTINGS, ""));
        return plan.build();
    }

    static String modeIcon(ModeKey mode) {
        return switch (mode.key()) {
            case "bedwars" -> "RED_BED";
            case "skywars" -> "GRASS_BLOCK";
            case "survivalgames" -> "STONE_SWORD";
            case "cores" -> "BEACON";
            case "ttt" -> "STICK";
            case "endergames" -> "ENDER_PEARL";
            case "gungame" -> "WOODEN_AXE";
            case "masterbuilders" -> "BRICKS";
            default -> "PAPER";
        };
    }

    private static Slot status(String material, String nameKey, Click click) {
        return new Slot(material, nameKey, null, List.of(), TagResolver.empty(), null, null, 1, click);
    }

    // ------------------------------------------------------------------ 5.6 Einstellungen

    /** Icon und Status-Slot einer Einstellung (§5.6). */
    public record SettingSlot(Setting setting, int icon, int status, String material, String key) {
    }

    public static final List<SettingSlot> SETTING_SLOTS = List.of(
            new SettingSlot(Setting.FRIEND_REQUESTS, 10, 19, "PLAYER_HEAD", "friends"),
            new SettingSlot(Setting.SHOW_ONLINE, 12, 21, "ENDER_EYE", "online"),
            new SettingSlot(Setting.MSG_FROM, 14, 23, "PAPER", "msg"),
            new SettingSlot(Setting.PARTY_FROM, 16, 25, "CAKE", "party"),
            new SettingSlot(Setting.JUMP_FROM, 28, 37, "ENDER_PEARL", "jump"),
            new SettingSlot(Setting.NOTIFY_FRIENDS, 30, 39, "BELL", "notify.friends"),
            new SettingSlot(Setting.NOTIFY_CLAN, 32, 41, "YELLOW_BANNER", "notify.clan"),
            new SettingSlot(Setting.GLOBAL_CHAT, 34, 43, "OAK_SIGN", "global"));

    /** Daten der Einstellungs-GUI. */
    public record SettingsView(Person viewer, PrivacySettings settings, String language) {
    }

    public static GuiPlan settings(SettingsView view, Texts texts) {
        GuiPlan.Builder plan = new GuiPlan.Builder("gui.settings.title", view.viewer().nameArg(), 6);
        plan.glassRange(LIGHT_BLUE_GLASS, 0, 8).glassRange(LIGHT_BLUE_GLASS, 46, 48).glassRange(LIGHT_BLUE_GLASS, 50,
                53);
        for (SettingSlot entry : SETTING_SLOTS) {
            int value = view.settings().value(entry.setting());
            Level level = level(entry.setting(), value);
            Component valueText = texts.render(valueKey(entry.setting(), value));
            Click toggle = new Click.Toggle(entry.setting());
            plan.slot(entry.icon(), new Slot(entry.material(), "gui.settings." + entry.key(), null,
                    List.of("gui.settings." + entry.key() + ".lore", "gui.settings.current"),
                    Placeholder.component("value", valueText),
                    entry.setting() == Setting.FRIEND_REQUESTS ? view.viewer().uuid() : null, null, 1, toggle));
            plan.slot(entry.status(), switch (level) {
                case FULL -> new Slot("LIME_DYE", entry.setting().isToggle() ? "gui.status.on" : "gui.status.full",
                        null, List.of(), Placeholder.component("value", valueText), null, null, 1, toggle);
                case LIMITED -> new Slot("ORANGE_DYE", "gui.status.limited", null, List.of(),
                        Placeholder.component("value", valueText), null, null, 1, toggle);
                case OFF -> status("GRAY_DYE", "gui.status.off", toggle);
            });
        }
        plan.item(45, "ARROW", "gui.social.back", List.of(), TagResolver.empty(), new Click.Action("back"));
        plan.item(49, "WRITABLE_BOOK", "gui.settings.language", List.of("gui.settings.language.lore"),
                Placeholder.unparsed("language", view.language()), Click.Commands.any("language"));
        return plan.build();
    }

    /** Status-Stufe eines Werts: voll (Lime), eingeschränkt (Orange), aus (Grau). */
    public enum Level {
        FULL,
        LIMITED,
        OFF
    }

    public static Level level(Setting setting, int value) {
        if (setting == Setting.HIDE_FROM_CLAN || setting == Setting.NICK_ON_JOIN || setting == Setting.NICK_KEEP) {
            return value == 1 ? Level.FULL : Level.OFF;
        }
        if (value == 0) {
            return Level.OFF;
        }
        return switch (setting) {
            case FRIEND_REQUESTS, MSG_FROM, PARTY_FROM -> value == 2 ? Level.FULL : Level.LIMITED;
            case JUMP_FROM -> value == 1 ? Level.FULL : Level.LIMITED;
            default -> Level.FULL;
        };
    }

    /** Wort für einen Wert ({@code gui.value.*}). */
    public static String valueKey(Setting setting, int value) {
        return switch (setting) {
            case FRIEND_REQUESTS -> value == 2 ? "gui.value.all" : value == 1 ? "gui.value.premium"
                    : "gui.value.nobody";
            case SHOW_ONLINE -> value == 1 ? "gui.value.visible" : "gui.value.hidden";
            case MSG_FROM, PARTY_FROM -> value == 2 ? "gui.value.all" : value == 1 ? "gui.value.friends"
                    : "gui.value.nobody";
            case JUMP_FROM -> value == 1 ? "gui.value.friends" : value == 2 ? "gui.value.favorites"
                    : "gui.value.nobody";
            default -> value == 1 ? "gui.value.on" : "gui.value.off";
        };
    }

    // ------------------------------------------------------------------ 5.7 Nicknamen

    /** Daten der Nick-GUI; {@code texture} = Skin des aktiven Nicks oder {@code null}. */
    public record NickView(UUID viewer, boolean nickOnJoin, boolean nickKeep, @Nullable String texture) {
    }

    public static GuiPlan nick(NickView view) {
        Click onJoin = new Click.Toggle(Setting.NICK_ON_JOIN);
        Click keep = new Click.Toggle(Setting.NICK_KEEP);
        return new GuiPlan.Builder("gui.nick.title", TagResolver.empty(), 4)
                .glassRange(LIGHT_BLUE_GLASS, 0, 7).glassRange(LIGHT_BLUE_GLASS, 27, 35)
                .item(8, RED_GLASS, "gui.nick.back", List.of(), TagResolver.empty(), new Click.Action("back"))
                .item(11, "NAME_TAG", "gui.nick.onjoin", List.of("gui.nick.onjoin.lore"), TagResolver.empty(), onJoin)
                .slot(15, new Slot("PLAYER_HEAD", "gui.nick.keep", null, List.of("gui.nick.keep.lore"),
                        TagResolver.empty(), view.texture() == null ? view.viewer() : null, view.texture(), 1, keep))
                .slot(20, view.nickOnJoin() ? status("LIME_DYE", "gui.status.on", onJoin)
                        : status("GRAY_DYE", "gui.status.off", onJoin))
                .slot(24, view.nickKeep() ? status("LIME_DYE", "gui.status.on", keep)
                        : status("GRAY_DYE", "gui.status.off", keep))
                .build();
    }
}
