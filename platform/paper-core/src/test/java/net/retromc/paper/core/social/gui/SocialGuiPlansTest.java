package net.retromc.paper.core.social.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.retromc.core.api.social.ClanJoinMode;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.paper.core.social.gui.GuiPlan.Click;
import net.retromc.paper.core.social.gui.GuiPlan.Slot;
import net.retromc.paper.core.social.gui.SocialGuiPlans.ClanMemberView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.FriendView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.PartyMemberView;
import net.retromc.paper.core.social.gui.SocialGuiPlans.Person;
import net.retromc.paper.core.social.gui.SocialGuiPlans.RequestView;
import org.junit.jupiter.api.Test;

/**
 * Abnahme A24: jede Klickaktion der GUIs aus Spec 01 §5 erzeugt genau den genannten Proxy-Befehl; Glas-Slots haben
 * keine Wirkung (Entnahme sperrt das GUI-Framework für alle Slots).
 */
class SocialGuiPlansTest {

    private static final Instant T = Instant.parse("2026-11-02T10:00:00Z");
    private static final SocialGuiPlans.Texts TEXTS = Component::text;
    private final Person leader = new Person(UUID.randomUUID(), "Leiter", "§6");
    private final Person member = new Person(UUID.randomUUID(), "Mitglied", "§a");

    private static Click.Commands commands(GuiPlan plan, int slot) {
        return assertInstanceOf(Click.Commands.class, plan.slot(slot).orElseThrow().click());
    }

    private static void glass(GuiPlan plan, String material, int... slots) {
        for (int slot : slots) {
            Slot item = plan.slot(slot).orElseThrow();
            assertTrue(item.isGlass(), "Slot " + slot);
            assertEquals(material, item.material());
            assertInstanceOf(Click.None.class, item.click());
        }
    }

    @Test
    void partymenue51() {
        SocialGuiPlans.PartyView view = new SocialGuiPlans.PartyView(leader.uuid(), "Leiter", 3, false, false,
                List.of(new PartyMemberView(leader, true, "lobby-1"), new PartyMemberView(member, false, "lobby-2")),
                2);
        GuiPlan plan = SocialGuiPlans.party(view, 0);
        assertEquals("gui.party.title", plan.titleKey());
        assertEquals(5, plan.rows());
        glass(plan, "PURPLE_STAINED_GLASS_PANE", 0, 1, 2, 3, 5, 6, 7, 8, 27, 31, 35);
        assertEquals("CAKE", plan.slot(4).orElseThrow().material());
        assertEquals("PLAYER_HEAD", plan.slot(9).orElseThrow().material());
        assertInstanceOf(Click.None.class, plan.slot(9).orElseThrow().click());
        Click.Commands head = commands(plan, 10);
        assertEquals("party promote Mitglied", head.forClick(true, false));
        assertEquals("party kick Mitglied", head.forClick(false, false));
        assertEquals("gui.party.confirm.kick", head.confirmKey());
        assertEquals("party public", commands(plan, 36).left());
        assertEquals("party requests an", commands(plan, 37).left());
        assertEquals("party jump", commands(plan, 38).left());
        assertEquals("party pull", commands(plan, 39).left());
        assertEquals(new Click.Suggest("/party invite "), plan.slot(41).orElseThrow().click());
        assertEquals("partytoggle", commands(plan, 43).left());
        assertEquals("party disband", commands(plan, 44).left());
        assertEquals("gui.party.disband", plan.slot(44).orElseThrow().nameKey());

        GuiPlan asMember = SocialGuiPlans.party(new SocialGuiPlans.PartyView(member.uuid(), "Leiter", 3, false, false,
                view.members(), 2), 0);
        assertInstanceOf(Click.None.class, asMember.slot(9).orElseThrow().click());
        assertEquals("party leave", commands(asMember, 44).left());

        GuiPlan none = SocialGuiPlans.party(new SocialGuiPlans.PartyView(member.uuid(), null, 0, false, false,
                List.of(), 2), 0);
        assertEquals("gui.party.create", none.slot(22).orElseThrow().nameKey());
    }

    @Test
    void freunde52UndOptionen() {
        FriendView online = new FriendView(member, true, "bedwars-1", null, T);
        GuiPlan plan = SocialGuiPlans.friends(new SocialGuiPlans.FriendsView(List.of(online), 250, 3, false), 0);
        assertEquals(6, plan.rows());
        Click.Split head = assertInstanceOf(Click.Split.class, plan.slot(0).orElseThrow().click());
        assertEquals("friend jump Mitglied", ((Click.Commands) head.left()).left());
        assertEquals(new Click.Open(SocialGuiPlans.FRIEND_OPTIONS, member.uuid().toString()), head.right());
        glass(plan, "RED_STAINED_GLASS_PANE", 46, 48, 50, 52);
        // Server-Test: eine Seite → keine Pfeile, Rahmen statt „Nächste Seite“ (Spec 02 §5.1).
        glass(plan, "RED_STAINED_GLASS_PANE", 45, 53);
        assertEquals(3, plan.slot(47).orElseThrow().amount());
        assertEquals(new Click.Open(SocialGuiPlans.FRIEND_REQUESTS, ""), plan.slot(47).orElseThrow().click());
        assertEquals(new Click.Open(SocialGuiPlans.SETTINGS, ""), plan.slot(49).orElseThrow().click());
        assertEquals(new Click.Action("favorites"), plan.slot(51).orElseThrow().click());

        GuiPlan options = SocialGuiPlans.friendOptions(member, true);
        assertEquals("friend jump Mitglied", commands(options, 10).left());
        assertEquals("party invite Mitglied", commands(options, 12).left());
        assertEquals("friend favorite Mitglied", commands(options, 14).left());
        assertEquals(new Click.Open(SocialGuiPlans.FRIEND_REMOVE, member.uuid().toString()),
                options.slot(16).orElseThrow().click());
        GuiPlan remove = SocialGuiPlans.friendRemove(member);
        assertEquals("LIME_CONCRETE", remove.slot(11).orElseThrow().material());
        assertEquals("friend remove Mitglied", commands(remove, 11).left());
        assertEquals("RED_CONCRETE", remove.slot(15).orElseThrow().material());
    }

    @Test
    void freundeBlaetternNurBeiBedarf() {
        List<FriendView> many = new ArrayList<>();
        for (int i = 0; i < SocialGuiPlans.LIST_HEADS + 1; i++) {
            many.add(new FriendView(new Person(UUID.randomUUID(), "Freund" + i, "§a"), false, null, T, T));
        }
        SocialGuiPlans.FriendsView view = new SocialGuiPlans.FriendsView(many, 250, 0, false);
        GuiPlan first = SocialGuiPlans.friends(view, 0);
        glass(first, "RED_STAINED_GLASS_PANE", 45);
        assertEquals(new Click.Action("next"), first.slot(53).orElseThrow().click());
        GuiPlan second = SocialGuiPlans.friends(view, 1);
        assertEquals(new Click.Action("prev"), second.slot(45).orElseThrow().click());
        glass(second, "RED_STAINED_GLASS_PANE", 53);
        assertTrue(second.slot(0).isPresent());
        assertTrue(second.slot(1).isEmpty());
        // Zu große Seite (z. B. nach Entfernen) zeigt die letzte.
        GuiPlan beyond = SocialGuiPlans.friends(view, 7);
        assertTrue(beyond.slot(0).isPresent());
        assertTrue(beyond.slot(1).isEmpty());
        glass(beyond, "RED_STAINED_GLASS_PANE", 53);
        assertEquals(1, SocialGuiPlans.pages(0, SocialGuiPlans.LIST_HEADS));
        assertEquals(2, SocialGuiPlans.pages(SocialGuiPlans.LIST_HEADS + 1, SocialGuiPlans.LIST_HEADS));
    }

    @Test
    void anfragen53() {
        GuiPlan plan = SocialGuiPlans.friendRequests(List.of(new RequestView(member, T)), 0);
        Click.Commands head = commands(plan, 0);
        assertEquals("friend accept Mitglied", head.forClick(true, false));
        assertEquals("friend deny Mitglied", head.forClick(false, false));
        assertEquals("friend acceptall", commands(plan, 48).left());
        assertEquals("friend denyall", commands(plan, 50).left());
        assertEquals(new Click.Open(SocialGuiPlans.FRIENDS, ""), plan.slot(45).orElseThrow().click());
        assertEquals("clan accept Mitglied", commands(SocialGuiPlans.clanRequests(List.of(new RequestView(member, T)),
                0), 0).left());
    }

    @Test
    void clan54Und55() {
        SocialGuiPlans.ClanView view = new SocialGuiPlans.ClanView("Retro_Kings", "RK", 30, 2, T, ClanRole.LEADER, 1,
                List.of(new ClanMemberView(leader, ClanRole.LEADER, true),
                        new ClanMemberView(member, ClanRole.TRIAL, false)));
        GuiPlan plan = SocialGuiPlans.clan(view, TEXTS, 0);
        glass(plan, "YELLOW_STAINED_GLASS_PANE", 0, 3, 5, 8, 46, 48, 50, 52);
        assertEquals("WHITE_BANNER", plan.slot(4).orElseThrow().material());
        Click.Commands head = commands(plan, 10);
        assertEquals("clan promote Mitglied", head.forClick(true, false));
        assertEquals("clan demote Mitglied", head.forClick(false, false));
        assertEquals("clan kick Mitglied", head.forClick(false, true));
        assertEquals("clan cwstats Retro_Kings", commands(plan, 47).left());
        assertEquals(new Click.Open(SocialGuiPlans.CLAN_SETTINGS, ""), plan.slot(49).orElseThrow().click());
        assertEquals(new Click.Open(SocialGuiPlans.CLAN_REQUESTS, ""), plan.slot(51).orElseThrow().click());
        assertEquals("clan leave", commands(plan, 53).left());
        assertEquals("gui.clan.confirm.leave", commands(plan, 53).confirmKey());
        GuiPlan noClan = SocialGuiPlans.clan(new SocialGuiPlans.ClanView(null, "", 0, 0, T, ClanRole.TRIAL, 0,
                List.of()), TEXTS, 0);
        assertEquals(new Click.Suggest("/clan create "), noClan.slot(22).orElseThrow().click());

        GuiPlan settings = SocialGuiPlans.clanSettings(new SocialGuiPlans.ClanSettingsView(ClanJoinMode.INVITE_ONLY,
                true, Set.of(ModeKey.BEDWARS), false));
        assertEquals("OAK_DOOR", settings.slot(11).orElseThrow().material());
        assertEquals("ORANGE_DYE", settings.slot(20).orElseThrow().material());
        assertEquals("LIME_DYE", settings.slot(22).orElseThrow().material());
        assertEquals("ENDER_EYE", settings.slot(26).orElseThrow().material());
        assertEquals(new Click.Toggle(Setting.HIDE_FROM_CLAN), settings.slot(35).orElseThrow().click());
        assertEquals("GRAY_DYE", settings.slot(35).orElseThrow().material());
        assertEquals("clan togglestats bedwars", commands(SocialGuiPlans.clanModes(Set.of()), 9).left());
    }

    @Test
    void einstellungen56UndNick57() {
        Person viewer = new Person(UUID.randomUUID(), "Ich", "§a");
        GuiPlan plan = SocialGuiPlans.settings(new SocialGuiPlans.SettingsView(viewer, PrivacySettings.DEFAULTS,
                "Deutsch"), TEXTS);
        glass(plan, "LIGHT_BLUE_STAINED_GLASS_PANE", 0, 8, 46, 53);
        for (SocialGuiPlans.SettingSlot entry : SocialGuiPlans.SETTING_SLOTS) {
            assertEquals(entry.material(), plan.slot(entry.icon()).orElseThrow().material());
            assertEquals(new Click.Toggle(entry.setting()), plan.slot(entry.status()).orElseThrow().click());
        }
        assertEquals("LIME_DYE", plan.slot(19).orElseThrow().material());
        assertEquals("ORANGE_DYE", plan.slot(23).orElseThrow().material());
        assertEquals("language", commands(plan, 49).left());
        assertEquals(SocialGuiPlans.Level.OFF, SocialGuiPlans.level(Setting.MSG_FROM, 0));
        assertEquals(SocialGuiPlans.Level.LIMITED, SocialGuiPlans.level(Setting.JUMP_FROM, 2));

        GuiPlan nick = SocialGuiPlans.nick(new SocialGuiPlans.NickView(viewer.uuid(), true, false, null));
        assertEquals("gui.nick.title", nick.titleKey());
        assertEquals(4, nick.rows());
        glass(nick, "LIGHT_BLUE_STAINED_GLASS_PANE", 0, 7, 27, 35);
        assertEquals("RED_STAINED_GLASS_PANE", nick.slot(8).orElseThrow().material());
        assertEquals("NAME_TAG", nick.slot(11).orElseThrow().material());
        assertEquals("PLAYER_HEAD", nick.slot(15).orElseThrow().material());
        assertEquals("LIME_DYE", nick.slot(20).orElseThrow().material());
        assertEquals("GRAY_DYE", nick.slot(24).orElseThrow().material());
        assertEquals(new Click.Toggle(Setting.NICK_ON_JOIN), nick.slot(20).orElseThrow().click());
        assertEquals(new Click.Toggle(Setting.NICK_KEEP), nick.slot(15).orElseThrow().click());
        assertNull(nick.slot(9).orElse(null));
    }

    /** Lobby-Profil-Item → Profil → Untermenüs (Spec 01 §4.5); „Zurück“ der Untermenüs führt ins Profil. */
    @Test
    void profilMenueFuehrtZuDenUntermenues() {
        UUID viewer = UUID.randomUUID();
        GuiPlan plan = SocialGuiPlans.profile(viewer);
        assertEquals("gui.profile.title", plan.titleKey());
        assertEquals(3, plan.rows());
        glass(plan, "LIGHT_BLUE_STAINED_GLASS_PANE", 0, 8, 18, 26);
        assertEquals(new Click.Open(SocialGuiPlans.FRIENDS, ""), plan.slot(10).orElseThrow().click());
        assertEquals(viewer, plan.slot(10).orElseThrow().head());
        assertEquals(new Click.Open(SocialGuiPlans.PARTY, ""), plan.slot(11).orElseThrow().click());
        assertEquals(new Click.Open(SocialGuiPlans.CLAN, ""), plan.slot(12).orElseThrow().click());
        assertEquals(new Click.Action("stats"), plan.slot(14).orElseThrow().click());
        assertEquals(new Click.Open(SocialGuiPlans.NICK, ""), plan.slot(15).orElseThrow().click());
        assertEquals(new Click.Open(SocialGuiPlans.SETTINGS, ""), plan.slot(16).orElseThrow().click());
        Person person = new Person(viewer, "Ich", "§a");
        GuiPlan settings = SocialGuiPlans.settings(new SocialGuiPlans.SettingsView(person, PrivacySettings.DEFAULTS,
                "Deutsch"), TEXTS);
        assertEquals(new Click.Action("back"), settings.slot(45).orElseThrow().click());
        assertEquals(new Click.Action("back"), SocialGuiPlans.nick(new SocialGuiPlans.NickView(viewer, false, false,
                null)).slot(8).orElseThrow().click());
    }
}
