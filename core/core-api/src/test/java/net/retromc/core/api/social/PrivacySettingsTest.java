package net.retromc.core.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrivacySettingsTest {

    private static final PrivacySettings DEFAULTS = PrivacySettings.DEFAULTS;

    @Test
    void standardwerteSpec01Abschnitt613() {
        for (Setting setting : Setting.values()) {
            assertEquals(setting.defaultValue(), DEFAULTS.value(setting), setting.name());
        }
        assertEquals(2, DEFAULTS.value(Setting.FRIEND_REQUESTS));
        assertEquals(1, DEFAULTS.value(Setting.MSG_FROM));
        assertEquals(2, DEFAULTS.value(Setting.PARTY_FROM));
        assertEquals(1, DEFAULTS.value(Setting.JUMP_FROM));
    }

    @Test
    void klickZyklenDerGui() {
        assertEquals(List.of(2, 1, 0), Setting.FRIEND_REQUESTS.cycle());
        assertEquals(List.of(1, 2, 0), Setting.JUMP_FROM.cycle());
        PrivacySettings settings = DEFAULTS;
        settings = settings.cycle(Setting.FRIEND_REQUESTS);
        assertEquals(1, settings.value(Setting.FRIEND_REQUESTS));
        settings = settings.cycle(Setting.FRIEND_REQUESTS);
        assertEquals(0, settings.value(Setting.FRIEND_REQUESTS));
        assertFalse(settings.friendRequests());
        settings = settings.cycle(Setting.FRIEND_REQUESTS);
        assertEquals(2, settings.value(Setting.FRIEND_REQUESTS));
        assertEquals(1, Setting.SHOW_ONLINE.next(0));
        assertEquals(0, Setting.SHOW_ONLINE.next(1));
        assertEquals(2, Setting.MSG_FROM.next(7), "unbekannter Wert springt auf den ersten");
        assertEquals(2, Setting.MSG_FROM.cycle().getFirst());
    }

    @Test
    void jedeEinstellungLaesstSichSetzen() {
        for (Setting setting : Setting.values()) {
            for (int value : setting.cycle()) {
                assertEquals(value, DEFAULTS.with(setting, value).value(setting), setting + "=" + value);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> DEFAULTS.with(Setting.SHOW_ONLINE, 2));
        assertEquals(200, Setting.HIDE_FROM_CLAN.requiredWeight());
        assertEquals(300, Setting.NICK_ON_JOIN.requiredWeight());
        assertEquals(0, Setting.GLOBAL_CHAT.requiredWeight());
    }

    @Test
    void freundschaftsanfragenNachFilter() {
        assertTrue(DEFAULTS.acceptsFriendRequestFrom(0));
        PrivacySettings premiumOnly = DEFAULTS.with(Setting.FRIEND_REQUESTS, 1);
        assertFalse(premiumOnly.acceptsFriendRequestFrom(0));
        assertTrue(premiumOnly.acceptsFriendRequestFrom(200));
        PrivacySettings nobody = DEFAULTS.with(Setting.FRIEND_REQUESTS, 0);
        assertFalse(nobody.acceptsFriendRequestFrom(1000));
    }

    @Test
    void friendToggleSchaltetNurDenMasterSchalter() {
        PrivacySettings premiumOnly = DEFAULTS.with(Setting.FRIEND_REQUESTS, 1);
        PrivacySettings off = premiumOnly.toggleFriendRequests();
        assertFalse(off.friendRequests());
        assertEquals(1, off.friendReqFrom());
        PrivacySettings on = off.toggleFriendRequests();
        assertTrue(on.friendRequests());
        assertEquals(1, on.friendReqFrom(), "Filter bleibt erhalten");
        PrivacySettings fromNobody = new PrivacySettings(false, 0, 1, 2, true, 1, true, true, false, true, false,
                false).toggleFriendRequests();
        assertEquals(2, fromNobody.friendReqFrom());
    }

    @Test
    void nachrichtenPartyUndNachspringen() {
        assertFalse(DEFAULTS.acceptsMessageFrom(false, false), "Standard nur Freunde");
        assertTrue(DEFAULTS.acceptsMessageFrom(true, false));
        assertTrue(DEFAULTS.with(Setting.MSG_FROM, 0).acceptsMessageFrom(false, true), "Team darf immer");
        assertTrue(DEFAULTS.acceptsPartyInviteFrom(false));
        assertFalse(DEFAULTS.with(Setting.PARTY_FROM, 1).acceptsPartyInviteFrom(false));
        assertFalse(DEFAULTS.with(Setting.PARTY_FROM, 0).acceptsPartyInviteFrom(true));
        assertTrue(DEFAULTS.allowsJumpFrom(true, false));
        assertFalse(DEFAULTS.allowsJumpFrom(false, false));
        PrivacySettings favoritesOnly = DEFAULTS.with(Setting.JUMP_FROM, 2);
        assertFalse(favoritesOnly.allowsJumpFrom(true, false));
        assertTrue(favoritesOnly.allowsJumpFrom(true, true));
        assertFalse(DEFAULTS.with(Setting.JUMP_FROM, 0).allowsJumpFrom(true, true));
    }
}
