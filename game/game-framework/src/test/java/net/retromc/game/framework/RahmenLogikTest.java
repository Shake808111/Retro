package net.retromc.game.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.retromc.game.framework.end.SubmitOnce;
import net.retromc.game.framework.kill.DamageTracker;
import net.retromc.game.framework.kill.HeartLine;
import net.retromc.game.framework.server.PrivateRoundContext;
import net.retromc.game.framework.shop.Purchase;
import net.retromc.game.framework.team.TeamColor;
import net.retromc.game.framework.team.TeamGuiLayout;
import net.retromc.game.framework.waiting.TipRotation;
import org.junit.jupiter.api.Test;

/** Reine Logik des Rahmens: Spec 03 §9 #11, #14, #16 und Bausteine (Tipps, Killer-Fenster, Bezahlen). */
class RahmenLogikTest {

    @Test
    void teamGuiSlotsJeTeamzahl() {
        assertEquals(List.of(11, 15), TeamGuiLayout.of(2).slots());
        assertEquals(List.of(11, 13, 15), TeamGuiLayout.of(3).slots());
        assertEquals(List.of(10, 12, 14, 16), TeamGuiLayout.of(4).slots());
        assertEquals(List.of(11, 12, 13, 14, 15), TeamGuiLayout.of(5).slots());
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16), TeamGuiLayout.of(7).slots());
        assertEquals(List.of(9, 10, 11, 12, 14, 15, 16, 17), TeamGuiLayout.of(8).slots());
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16, 19, 20), TeamGuiLayout.of(9).slots());
        assertEquals(3, TeamGuiLayout.of(4).rows());
        assertEquals(22, TeamGuiLayout.of(4).leave());
        assertEquals(4, TeamGuiLayout.of(8).rows());
        assertEquals(6, TeamGuiLayout.of(28).rows());
        assertEquals(43, TeamGuiLayout.of(28).slots().getLast());
    }

    @Test
    void herzzeile() {
        assertEquals("§c♥".repeat(9) + "§f♡", HeartLine.of(19.0));
        assertEquals("§c♥".repeat(10), HeartLine.of(20));
        assertEquals("§f♡".repeat(10), HeartLine.of(1));
        assertEquals("§f♡".repeat(10), HeartLine.of(0));
        assertEquals("§c♥".repeat(10), HeartLine.of(40));
    }

    @Test
    void statistikGenauEinmalJeMatch() {
        SubmitOnce<String> once = new SubmitOnce<>();
        AtomicInteger calls = new AtomicInteger();
        UUID match = UUID.randomUUID();
        CompletableFuture<String> first = once.run(match, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("ok");
        });
        CompletableFuture<String> second = once.run(match, () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("doppelt");
        });
        assertEquals(1, calls.get());
        assertSame(first, second);
        assertTrue(once.done(match));
        once.run(UUID.randomUUID(), () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("andere Runde");
        });
        assertEquals(2, calls.get());
    }

    @Test
    void tippsOhneWiederholungBisDurch() {
        List<String> keys = List.of("a", "b", "c", "d", "e");
        TipRotation<String> rotation = new TipRotation<>(keys, new Random(7));
        String last = null;
        for (int round = 0; round < 20; round++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < keys.size(); i++) {
                String tip = rotation.next();
                assertTrue(seen.add(tip), "Wiederholung in Runde " + round);
                if (i == 0) {
                    assertFalse(tip.equals(last), "derselbe Tipp direkt nacheinander");
                }
                last = tip;
            }
        }
    }

    @Test
    void killerFensterZehnSekunden() {
        DamageTracker tracker = new DamageTracker();
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        Instant hit = Instant.parse("2026-11-03T12:00:00Z");
        tracker.hit(victim, attacker, hit);
        assertEquals(Optional.of(attacker), tracker.lastAttacker(victim, hit.plusSeconds(10)));
        assertEquals(Optional.empty(), tracker.lastAttacker(victim, hit.plusSeconds(11)));
        tracker.hit(victim, victim, hit);
        assertEquals(Optional.of(attacker), tracker.lastAttacker(victim, hit), "Selbsttreffer zählt nicht");
        tracker.forget(attacker);
        assertEquals(Optional.empty(), tracker.lastAttacker(victim, hit));
    }

    @Test
    void bezahlenMitStapeln() {
        Purchase.Result exact = Purchase.tryBuy(List.of(8), 8);
        assertTrue(exact.paid());
        assertEquals(List.of(0), exact.remaining());
        Purchase.Result missing = Purchase.tryBuy(List.of(3, 4), 8);
        assertFalse(missing.paid());
        assertEquals(1, missing.missing());
        assertEquals(List.of(3, 4), missing.remaining());
        Purchase.Result stacks = Purchase.tryBuy(List.of(64, 64, 10), 100);
        assertTrue(stacks.paid());
        assertEquals(List.of(0, 28, 10), stacks.remaining());
        assertEquals(12, Purchase.tryBuy(List.of(), 12).missing());
    }

    @Test
    void privatrundeAusUmgebung() {
        UUID owner = UUID.randomUUID();
        PrivateRoundContext context = PrivateRoundContext.fromEnv(Map.of("PRIVATE_ID", "abc",
                "PRIVATE_OWNER", owner.toString())::get);
        assertEquals("abc", context.current().orElseThrow().id());
        assertEquals(owner, context.current().orElseThrow().owner());
        assertTrue(PrivateRoundContext.fromEnv(Map.<String, String>of()::get).current().isEmpty());
        assertNull(PrivateRoundContext.fromEnv(Map.of("PRIVATE_ID", "x", "PRIVATE_OWNER", "kaputt")::get)
                .current().orElseThrow().owner());
    }

    @Test
    void teamfarbenUndReihenfolgen() {
        assertEquals(List.of("red", "blue", "green", "yellow", "aqua", "purple", "pink", "orange"),
                TeamColor.FRAME_ORDER.subList(0, 8).stream().map(TeamColor::id).toList());
        assertEquals("§c", TeamColor.RED.color());
        assertEquals("lime_wool", TeamColor.GREEN.wool());
        assertEquals("light_blue_wool", TeamColor.AQUA.wool());
        List<String> bedwars = new ArrayList<>();
        TeamColor.BEDWARS_ORDER.forEach(color -> bedwars.add(color.id()));
        assertEquals(List.of("red", "blue", "yellow", "green", "pink", "aqua", "orange", "purple", "cyan", "magenta",
                "brown", "dark_green"), bedwars);
        assertEquals("#C040C0", TeamColor.MAGENTA.color());
        assertEquals("#8B5A2B", TeamColor.BROWN.color());
        assertEquals("§2", TeamColor.DARK_GREEN.color());
        assertEquals("green_wool", TeamColor.DARK_GREEN.wool());
    }
}
