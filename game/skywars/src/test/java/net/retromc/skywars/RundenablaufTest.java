package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.retromc.game.framework.kill.KillFeed;
import net.retromc.game.framework.kill.KillOptions;
import net.retromc.skywars.phase.RoundPlan;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #11 (Phasenfolge und Zeiten: Teleport → 3-2-1 → Aufbauphase/Schutzzeit 30 s → Kampf → Border-Ansage
 * 600 s, bei 2 Teams 60 s) und #13 (Reihenfolge der Kill-Meldungen).
 */
class RundenablaufTest {

    private final RoundPlan plan = new RoundPlan(SkyWarsConfig.DEFAULT.cageSeconds(),
            SkyWarsConfig.DEFAULT.protectionSeconds(), SkyWarsConfig.DEFAULT.borderDelay(),
            SkyWarsConfig.DEFAULT.borderTwoTeams());

    @Test
    void phasenfolgeUndZeiten() {
        // 0 s Teleport, 3-2-1 im Käfig
        assertEquals(RoundPlan.Phase.CAGE, plan.phase(0));
        assertEquals(List.of(3, 2, 1), List.of(plan.cageCountdown(0).getAsInt(), plan.cageCountdown(1).getAsInt(),
                plan.cageCountdown(2).getAsInt()));
        assertEquals(OptionalInt.empty(), plan.cageCountdown(3));
        // Käfig auf: Aufbauphase/Schutzzeit 30 s
        assertEquals(3, plan.protectionStart());
        assertEquals(RoundPlan.Phase.PROTECTION, plan.phase(3));
        assertEquals(RoundPlan.Phase.PROTECTION, plan.phase(32));
        assertEquals(33, plan.fightStart());
        assertEquals(RoundPlan.Phase.FIGHT, plan.phase(33));
        List<Integer> announced = new ArrayList<>();
        for (int second = 0; second < 40; second++) {
            plan.protectionAnnounce(second).ifPresent(announced::add);
        }
        assertEquals(List.of(30, 20, 10, 3, 2, 1), announced, "Ansagen der Schutzzeit (B)");
        // Kampf: Border-Ansage 600 s, Start bei Kampf + 600 s
        assertEquals(600, plan.borderStart(null) - plan.fightStart());
        List<Integer> border = new ArrayList<>();
        for (int second = plan.fightStart(); second <= plan.borderStart(null); second++) {
            plan.borderAnnounce(second, null).ifPresent(border::add);
        }
        assertEquals(List.of(300, 60, 30, 10, 5, 4, 3, 2, 1), border, "600 kommt mit dem Kampfbeginn");
    }

    @Test
    void borderBeiZweiTeamsNach60Sekunden() {
        int twoTeams = plan.fightStart() + 100;
        assertTrue(plan.twoTeamsShortens(twoTeams));
        assertEquals(twoTeams + 60, plan.borderStart(twoTeams));
        // während der Schutzzeit zählt die Minute ab Kampfbeginn
        assertEquals(plan.fightStart() + 60, plan.borderStart(10));
        // kurz vor der regulären Border: die frühere Zeit gilt, keine Verkürzung
        int late = plan.borderStart(null) - 30;
        assertFalse(plan.twoTeamsShortens(late));
        assertEquals(plan.borderStart(null), plan.borderStart(late));
        List<Integer> announced = new ArrayList<>();
        for (int second = twoTeams + 1; second <= twoTeams + 60; second++) {
            plan.borderAnnounce(second, twoTeams).ifPresent(announced::add);
        }
        assertEquals(List.of(30, 10, 5, 4, 3, 2, 1), announced);
    }

    @Test
    void killReihenfolgeImChat() {
        KillOptions options = SkyWarsMode.killOptions(20, "Engel", 5);
        assertEquals(List.of(KillFeed.Step.VICTIM_KIT, KillFeed.Step.KILL, KillFeed.Step.KILLER_HEALTH,
                KillFeed.Step.REMAINING), KillFeed.plan(options, true, true),
                "hatte das [Kit]-Kit → wurde von … getötet → Leben von … (Opfer) → N verbleibende Spieler");
        assertTrue(options.xpInBroadcast() && options.xpKillerOnly(), "(+20 XP) nur in der Ansicht des Killers");
        assertEquals(List.of(KillFeed.Step.DEATH, KillFeed.Step.REMAINING), KillFeed.plan(options, false, true),
                "ohne Killer: ist gestorben → verbleibende Spieler");
        assertEquals(List.of(KillFeed.Step.KILL, KillFeed.Step.KILLER_HEALTH, KillFeed.Step.REMAINING),
                KillFeed.plan(SkyWarsMode.killOptions(20, null, 5), true, true), "ohne Kit keine Kit-Zeile");
    }

    @Test
    void punkteXpUndCoins() {
        // Ranking-Punkte 1 × Kill + 5 × Sieg (Spec 04 §6.2.3, Spec 02 points_formula)
        assertEquals(7, SkyWarsRewards.points(2, true));
        assertEquals(3, SkyWarsRewards.points(3, false));
        SkyWarsConfig.Rewards rewards = SkyWarsConfig.Rewards.DEFAULT;
        // Beispiel der Spec: 2 Kills → 118 XP (2 × 20 + Sieg 50 + 7 Minuten Überleben = 28)
        int xp = 2 * rewards.kill() + rewards.endXp(true, 7 * 60);
        assertEquals(118, xp);
        assertEquals(2L * 118 + 10 * 2, rewards.coins(xp, 2));
        assertEquals(0, rewards.endXp(false, 14));
    }
}
