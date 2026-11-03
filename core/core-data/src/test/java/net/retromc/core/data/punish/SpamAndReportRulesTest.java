package net.retromc.core.data.punish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatVerdict;
import net.retromc.core.api.punish.ReportReason;
import net.retromc.core.api.punish.ReportResult;
import org.junit.jupiter.api.Test;

/** Anti-Spam (Spec 01 §6.16, A20) und Report-Regeln (§6.9, A21) ohne Datenzugriff. */
class SpamAndReportRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-02T12:00:00Z");
    private static final ChatContext PLAYER = new ChatContext(ChatContext.Source.CHAT, 0, false);
    private static final ChatContext PREMIUM_PLUS = new ChatContext(ChatContext.Source.CHAT, 300, false);
    private static final ChatContext TEAM = new ChatContext(ChatContext.Source.CHAT, 600, true);

    private final SpamFilter filter = new SpamFilter(Set.of("retromc.net"));

    private SpamFilter.Decision send(SpamFilter.State state, String text, ChatContext context, Duration after) {
        return filter.check(state, text, context, T0.plus(after));
    }

    @Test
    void mindestabstandJeRang() {
        SpamFilter.State first = send(SpamFilter.State.EMPTY, "hallo", PLAYER, Duration.ZERO).state();
        ChatCheck blocked = send(first, "noch was", PLAYER, Duration.ofMillis(1000)).check();
        assertEquals(ChatVerdict.SPAM_WAIT, blocked.verdict());
        assertEquals(Duration.ofMillis(500), blocked.retryAfter());
        assertEquals(ChatVerdict.OK, send(first, "noch was", PLAYER, Duration.ofMillis(1500)).check().verdict());

        SpamFilter.State premium = send(SpamFilter.State.EMPTY, "hallo", PREMIUM_PLUS, Duration.ZERO).state();
        assertEquals(ChatVerdict.OK, send(premium, "noch was", PREMIUM_PLUS, Duration.ofMillis(800)).check()
                .verdict());
        assertEquals(ChatVerdict.SPAM_WAIT, send(premium, "x", PREMIUM_PLUS, Duration.ofMillis(700)).check()
                .verdict());
    }

    @Test
    void wiederholungIn30SekundenAusserGg() {
        SpamFilter.State state = send(SpamFilter.State.EMPTY, "Kauft  Sachen", PLAYER, Duration.ZERO).state();
        assertEquals(ChatVerdict.REPEAT, send(state, "kauft sachen", PLAYER, Duration.ofSeconds(10)).check()
                .verdict());
        assertEquals(ChatVerdict.OK, send(state, "kauft sachen", PLAYER, Duration.ofSeconds(30)).check().verdict());
        SpamFilter.State gg = send(SpamFilter.State.EMPTY, "gg", PLAYER, Duration.ZERO).state();
        assertEquals(ChatVerdict.OK, send(gg, "GG", PLAYER, Duration.ofSeconds(2)).check().verdict());
        SpamFilter.State gl = send(SpamFilter.State.EMPTY, "gl", PLAYER, Duration.ZERO).state();
        assertEquals(ChatVerdict.OK, send(gl, "gl", PLAYER, Duration.ofSeconds(2)).check().verdict());
    }

    @Test
    void burstSperrtZehnSekunden() {
        SpamFilter.State state = SpamFilter.State.EMPTY;
        for (int i = 0; i < 5; i++) {
            SpamFilter.Decision decision = send(state, "nachricht " + i, PREMIUM_PLUS, Duration.ofMillis(800L * i));
            assertEquals(ChatVerdict.OK, decision.check().verdict());
            state = decision.state();
        }
        SpamFilter.Decision sixth = send(state, "nachricht 5", PREMIUM_PLUS, Duration.ofMillis(4000));
        assertEquals(ChatVerdict.BURST, sixth.check().verdict());
        assertEquals(Duration.ofSeconds(10), sixth.check().retryAfter());
        SpamFilter.Decision locked = send(sixth.state(), "später", PREMIUM_PLUS, Duration.ofMillis(9000));
        assertEquals(ChatVerdict.BURST, locked.check().verdict());
        assertEquals(Duration.ofSeconds(5), locked.check().retryAfter());
        assertEquals(ChatVerdict.OK, send(locked.state(), "jetzt", PREMIUM_PLUS, Duration.ofSeconds(15)).check()
                .verdict());
    }

    @Test
    void grossbuchstabenWerbungUndTeam() {
        ChatCheck caps = send(SpamFilter.State.EMPTY, "HALLO LEUTE wie gehts", PLAYER, Duration.ZERO).check();
        assertEquals(ChatVerdict.CAPS_FIXED, caps.verdict());
        assertEquals("hallo leute wie gehts", caps.text());
        assertTrue(caps.verdict().allowed());
        assertEquals(ChatVerdict.OK, send(SpamFilter.State.EMPTY, "OK GG", PLAYER, Duration.ZERO).check().verdict());
        assertEquals(ChatVerdict.AD, send(SpamFilter.State.EMPTY, "kommt auf mein-server.de", PLAYER, Duration.ZERO)
                .check().verdict());
        assertEquals(ChatVerdict.AD, send(SpamFilter.State.EMPTY, "ip 192.168.10.20", PLAYER, Duration.ZERO)
                .check().verdict());
        assertEquals(ChatVerdict.OK, send(SpamFilter.State.EMPTY, "shop.retromc.net ist offen", PLAYER,
                Duration.ZERO).check().verdict());
        assertFalse(ChatVerdict.AD.allowed());

        SpamFilter.State state = send(SpamFilter.State.EMPTY, "TEAM SAGT HALLO", TEAM, Duration.ZERO).state();
        ChatCheck team = send(state, "TEAM SAGT HALLO", TEAM, Duration.ofMillis(10)).check();
        assertEquals(ChatVerdict.OK, team.verdict());
        assertEquals("TEAM SAGT HALLO", team.text());
    }

    @Test
    void zustandUeberlebtDenHash() {
        SpamFilter.State state = send(SpamFilter.State.EMPTY, "hallo", PLAYER, Duration.ZERO).state();
        assertEquals(state, SpamFilter.State.fromHash(state.toHash()));
        assertEquals(SpamFilter.State.EMPTY, SpamFilter.State.fromHash(null));
    }

    @Test
    void reportRegeln() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(Optional.of(ReportResult.Outcome.SELF), ReportRules.precheck(a, a, ReportReason.HACKING,
                "bedwars"));
        assertEquals(Optional.of(ReportResult.Outcome.REASON_NOT_ALLOWED),
                ReportRules.precheck(a, b, ReportReason.RANDOMKILLING, "bedwars"));
        assertTrue(ReportRules.precheck(a, b, ReportReason.RANDOMKILLING, "TTT").isEmpty());
        assertTrue(ReportRules.precheck(a, b, ReportReason.RANDOMKILLING, "replay").isEmpty());
        assertEquals(Optional.of(ReportResult.Outcome.COOLDOWN), ReportRules.limits(true, 0));
        assertEquals(Optional.of(ReportResult.Outcome.LIMIT), ReportRules.limits(false, 10));
        assertTrue(ReportRules.limits(false, 9).isEmpty());
    }
}
