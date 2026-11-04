package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.i18n.Messages;
import net.retromc.skywars.board.LevelTag;
import net.retromc.skywars.challenge.Challenge;
import net.retromc.skywars.challenge.ChallengeSet;
import net.retromc.skywars.challenge.ChallengeTracker;
import net.retromc.skywars.event.GlobalEventSchedule;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #16 (Chestrefill: Zeitplan, Meldung {@code Globales Event: Chestrefill (Stufe 1)}), Challenges §6.2.6
 * (Scheitern sofort, Erfolg am Ende) und Modus-Level im Chat/Nametag.
 */
class EventsUndChallengesTest {

    @Test
    void chestrefillZeitplanUndMeldung() throws IOException {
        GlobalEventSchedule schedule;
        try (Reader reader = Resources.open("events.yml")) {
            schedule = GlobalEventSchedule.load(reader);
        }
        assertEquals(GlobalEventSchedule.DEFAULT, schedule, "Stufe 1 bei Kampf + 5:00, Stufe 2 bei + 10:00");
        assertEquals(List.of(), schedule.due(299));
        GlobalEventSchedule.Entry first = schedule.due(300).getFirst();
        assertEquals(GlobalEventSchedule.CHESTREFILL, first.type());
        assertEquals(1, first.stage());
        assertEquals(2, schedule.due(600).getFirst().stage());
        assertEquals("chestrefill2", schedule.byId("CHESTREFILL2").orElseThrow().id(), "/swdebug event <id>");
        String event = plain(Messages.raw(Locale.GERMANY, "sw.event.chestrefill"));
        String line = plain(Messages.raw(Locale.GERMANY, "sw.event"), Placeholder.unparsed("event", event),
                Placeholder.unparsed("n", Integer.toString(first.stage())), Placeholder.unparsed("prefix", ""));
        assertEquals("Globales Event: Chestrefill (Stufe 1)", line);
    }

    private static String plain(String miniMessage, TagResolver... args) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage,
                args));
    }

    @Test
    void challengesScheiternSofortUndZaehlen() {
        ChallengeTracker noSword = new ChallengeTracker(Challenge.NO_SWORD);
        assertEquals(ChallengeTracker.Change.NONE, noSword.kill());
        assertEquals(ChallengeTracker.Change.FAILED, noSword.swordUsed(), "sofort melden");
        assertEquals(ChallengeTracker.Change.NONE, noSword.swordUsed(), "nur einmal");
        assertFalse(noSword.success(true));

        ChallengeTracker kills = new ChallengeTracker(Challenge.KILLS_3);
        kills.kill();
        kills.kill();
        assertEquals(2f / 3f, kills.share(), 1e-6);
        assertFalse(kills.success(false));
        kills.kill();
        assertTrue(kills.success(false), "Zählziel ohne Sieg");
        assertEquals(ChallengeTracker.Change.NONE, kills.died(false), "Ziel erreicht");

        ChallengeTracker chests = new ChallengeTracker(Challenge.CHESTS_10);
        chests.chestOpened();
        assertEquals(ChallengeTracker.Change.FAILED, chests.died(false), "Tod vor dem Ziel");

        ChallengeTracker mid = new ChallengeTracker(Challenge.NO_MID);
        assertEquals(ChallengeTracker.Change.NONE, mid.died(true), "Team kann noch gewinnen");
        assertTrue(mid.success(true));
        assertEquals(ChallengeTracker.Change.FAILED, new ChallengeTracker(Challenge.NO_ARMOR).died(false));
        assertEquals(ChallengeTracker.Change.FAILED, new ChallengeTracker(Challenge.NO_ARMOR).armorWorn());
    }

    @Test
    void challengeKatalog() throws IOException {
        ChallengeSet set;
        try (Reader reader = Resources.open("challenges.yml")) {
            set = ChallengeSet.load(reader);
        }
        assertEquals(ChallengeSet.DEFAULT, set);
        assertEquals(30, set.xpOf(Challenge.CHESTS_10));
        assertEquals(50, set.xpOf(Challenge.NO_SWORD));
        ChallengeSet reduced = ChallengeSet.load(new StringReader("challenges:\n  no_mid: {enabled: false}\n"
                + "  kills_3: {xp: 70}\n"));
        assertEquals(Set.of(Challenge.NO_SWORD, Challenge.KILLS_3, Challenge.NO_ARMOR, Challenge.CHESTS_10),
                reduced.xp().keySet());
        assertEquals(70, reduced.xpOf(Challenge.KILLS_3));
        assertNull(new ChallengeSet(Map.of()).pick(new Random(1)));
        Random random = new Random(5);
        for (int i = 0; i < 100; i++) {
            assertTrue(set.xp().containsKey(set.pick(random)));
        }
    }

    @Test
    void levelImChatUndAmNametag() {
        LevelTag tag = new LevelTag(SkyWarsConfig.DEFAULT.levelColors(), SkyWarsConfig.DEFAULT.starFrom());
        assertEquals("§7", tag.color(1));
        assertEquals("§a", tag.color(60));
        assertEquals("38", tag.text(38));
        assertEquals("300✪", tag.text(300), "✪ ab 300");
        assertEquals("§b", tag.color(350));
    }
}
