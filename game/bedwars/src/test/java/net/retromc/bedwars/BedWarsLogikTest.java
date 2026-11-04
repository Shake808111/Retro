package net.retromc.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.retromc.bedwars.board.BoardScores;
import net.retromc.bedwars.spawner.GeneratorSchedule;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.bedwars.spawner.ResourceList;
import net.retromc.bedwars.trap.TrapQueue;
import net.retromc.bedwars.trap.TrapType;
import net.retromc.bedwars.upgrade.TeamUpgrades;
import net.retromc.bedwars.upgrade.UpgradeType;
import org.junit.jupiter.api.Test;

/** Spec 04 §9 BedWarsLogik: #1, #2, #3, #4, #10 und U1–U3, U6, U9. */
class BedWarsLogikTest {

    private static String board(GeneratorSchedule schedule, Duration at) {
        GeneratorSchedule.State state = schedule.at(at);
        return GeneratorSchedule.roman(state.shown()) + " "
                + (state.max() ? "Max." : GeneratorSchedule.clock(state.untilNext()));
    }

    @Test
    void generatorZeitplanAufDemBoard() {
        GeneratorSchedule diamond = GeneratorSchedule.diamond();
        GeneratorSchedule gold = GeneratorSchedule.gold();
        assertEquals("II 02:00", board(diamond, Duration.ofMinutes(3)));
        assertEquals("II 07:00", board(gold, Duration.ofMinutes(3)));
        assertEquals("III 03:00", board(diamond, Duration.ofMinutes(12)));
        assertEquals("III 13:00", board(gold, Duration.ofMinutes(12)));
        assertEquals("III Max.", board(diamond, Duration.ofMinutes(15)));
        assertEquals("III 10:00", board(gold, Duration.ofMinutes(15)));
        assertEquals("III Max.", board(gold, Duration.ofMinutes(25)));
        assertEquals("III Max.", board(diamond, Duration.ofMinutes(25)));
    }

    @Test
    void stufenGrenzen() {
        GeneratorSchedule diamond = GeneratorSchedule.diamond();
        assertEquals(1, diamond.at(Duration.ZERO).tier());
        assertEquals(1, diamond.at(Duration.ofSeconds(299)).tier());
        assertEquals(2, diamond.at(Duration.ofMinutes(5)).tier());
        assertEquals(2, diamond.at(Duration.ofSeconds(899)).tier());
        assertEquals(3, diamond.at(Duration.ofMinutes(15)).tier());
        assertEquals(Duration.ofSeconds(25), diamond.interval(Duration.ZERO));
        assertEquals(Duration.ofSeconds(20), diamond.interval(Duration.ofMinutes(6)));
        assertEquals(Duration.ofSeconds(15), diamond.interval(Duration.ofMinutes(16)));
        GeneratorSchedule gold = GeneratorSchedule.gold();
        assertEquals(Duration.ofSeconds(35), gold.interval(Duration.ZERO));
        assertEquals(Duration.ofSeconds(25), gold.interval(Duration.ofSeconds(24 * 60 + 59)));
        assertEquals(Duration.ofSeconds(15), gold.interval(Duration.ofMinutes(25)));
        assertEquals(2, gold.tierStartingAt(Duration.ofMinutes(10)));
        assertEquals(0, gold.tierStartingAt(Duration.ofMinutes(11)));
    }

    @Test
    void spawnTakte() {
        // Kupfer 60 ± 1 Items/min, Eisen 7–8/min an jedem Basis-Spawner (Stufe I)
        assertEquals(60, Duration.ofMinutes(1).dividedBy(GeneratorSchedule.fixed(Resource.COPPER,
                Duration.ofSeconds(1)).interval(Duration.ZERO)));
        long iron = Duration.ofMinutes(1).dividedBy(GeneratorSchedule.fixed(Resource.IRON, Duration.ofSeconds(8))
                .interval(Duration.ZERO));
        assertTrue(iron >= 7 && iron <= 8);
        assertEquals(List.of(64, 16, 8, 4), List.of(Resource.COPPER.cap(), Resource.IRON.cap(), Resource.GOLD.cap(),
                Resource.DIAMOND.cap()));
        assertEquals(0.85, UpgradeType.resourceFactor(1), 1e-9);
        assertEquals(0.25, UpgradeType.resourceFactor(5), 1e-9);
    }

    @Test
    void boardKopfScores() {
        assertEquals(List.of(7, 6, 5, 4, 3, 2), BoardScores.header(1));
        assertEquals(List.of(10, 9, 8, 7, 6, 5), BoardScores.header(4));
        assertEquals(List.of(11, 10, 9, 8, 7, 6), BoardScores.header(5));
    }

    @Test
    void boardSortierungEigenesTeamZuerst() {
        List<BoardScores.TeamLine> teams = List.of(new BoardScores.TeamLine(0, 2, true),
                new BoardScores.TeamLine(1, 3, true), new BoardScores.TeamLine(2, 2, false),
                new BoardScores.TeamLine(3, 0, true));
        assertEquals(List.of(1, 2, 0, 3), BoardScores.sort(teams, 2).stream().map(BoardScores.TeamLine::index)
                .toList());
        assertEquals(List.of(1, 0, 2, 3), BoardScores.sort(teams, -1).stream().map(BoardScores.TeamLine::index)
                .toList());
    }

    @Test
    void ressourcenListeNurPositivInReihenfolge() {
        Map<Resource, Integer> amounts = new EnumMap<>(Resource.class);
        amounts.put(Resource.DIAMOND, 8);
        amounts.put(Resource.COPPER, 160);
        amounts.put(Resource.GOLD, 3);
        amounts.put(Resource.IRON, 0);
        List<ResourceList.Entry> entries = ResourceList.entries(amounts);
        assertEquals(List.of("bw.resources.copper", "bw.resources.gold", "bw.resources.diamond"),
                entries.stream().map(ResourceList.Entry::key).toList());
        assertEquals("", ResourceList.verbSuffix(entries));
        List<ResourceList.Entry> one = ResourceList.entries(Map.of(Resource.DIAMOND, 1));
        assertEquals("bw.resources.diamond.one", one.getFirst().key());
        assertEquals("", ResourceList.verbSuffix(one));
        assertEquals("n", ResourceList.verbSuffix(ResourceList.entries(Map.of(Resource.DIAMOND, 8))));
        assertTrue(ResourceList.entries(Map.of(Resource.IRON, 0)).isEmpty());
        assertEquals(List.of(4, 3, 3), ResourceList.split(10, 3));
    }

    @Test
    void fallenWarteschlange() {
        TrapQueue queue = new TrapQueue();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        assertFalse(queue.armed(now));
        assertTrue(queue.add(TrapType.BLINDNESS));
        assertTrue(queue.add(TrapType.WEAKNESS));
        assertTrue(queue.add(TrapType.MINING_FATIGUE));
        assertFalse(queue.add(TrapType.LIGHTNING), "höchstens 3 Fallen");
        assertEquals(10, queue.nextRadius());
        assertEquals(TrapType.BLINDNESS, queue.trigger(now).orElseThrow());
        assertFalse(queue.armed(now.plusSeconds(29)), "30 s Sperre");
        assertTrue(queue.trigger(now.plusSeconds(29)).isEmpty());
        assertEquals(TrapType.WEAKNESS, queue.trigger(now.plusSeconds(30)).orElseThrow());
        assertEquals(6, queue.nextRadius(), "Abbaulähmung verkleinert");
        assertEquals(2, TrapType.WEAKNESS.cost());
    }

    @Test
    void schwaechefalleSenktUpgradesSechzigSekunden() {
        TeamUpgrades upgrades = new TeamUpgrades();
        UUID intruder = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        assertEquals(1, upgrades.raise(UpgradeType.ARMOR));
        assertEquals(2, upgrades.raise(UpgradeType.ARMOR));
        upgrades.weaken(intruder, now);
        assertEquals(1, upgrades.effective(UpgradeType.ARMOR, intruder, now.plusSeconds(59)));
        assertEquals(0, upgrades.effective(UpgradeType.WEAPONS, intruder, now), "nie unter 0");
        assertEquals(2, upgrades.effective(UpgradeType.ARMOR, intruder, now.plusSeconds(60)));
        assertEquals(2, upgrades.effective(UpgradeType.ARMOR, UUID.randomUUID(), now));
    }

    @Test
    void upgradeKosten() {
        assertEquals(List.of(4, 12), costs(UpgradeType.WEAPONS));
        assertEquals(List.of(2, 8, 14, 20), costs(UpgradeType.ARMOR));
        assertEquals(List.of(2, 6), costs(UpgradeType.MINING));
        assertEquals(List.of(3, 6), costs(UpgradeType.FEATHER));
        assertEquals(List.of(3, 6), costs(UpgradeType.BASE));
        assertEquals(List.of(2, 4, 6, 8, 12), costs(UpgradeType.RESOURCES));
        TeamUpgrades upgrades = new TeamUpgrades();
        upgrades.raise(UpgradeType.WEAPONS);
        upgrades.raise(UpgradeType.WEAPONS);
        assertTrue(upgrades.maxed(UpgradeType.WEAPONS));
        assertThrows(IllegalStateException.class, () -> upgrades.raise(UpgradeType.WEAPONS));
    }

    private static List<Integer> costs(UpgradeType type) {
        return java.util.stream.IntStream.rangeClosed(1, type.maxLevel()).map(type::cost).boxed().toList();
    }

    @Test
    void punkteXpCoinsUndNurFinalKills() {
        assertEquals(5 * 3 + 10 + 50, BedWarsRewards.points(3, 1, true));
        BedWarsRewards rewards = BedWarsRewards.DEFAULT;
        assertEquals(3 * 25 + 10 + 50 + 20 + 20, rewards.totalXp(3, 1, true, true, 600));
        assertEquals(Math.round(181 * 2.5), rewards.coins(181));
        BwPlayer player = new BwPlayer(UUID.randomUUID());
        player.kill(false);
        player.kill(true);
        player.kill(false);
        assertEquals(3, player.kills());
        assertEquals(1, player.finalKills(), "Statistik zählt nur Final Kills (Spec 04 §9 #10)");
        assertTrue(player.armor(2));
        assertFalse(player.armor(1), "niedrigere Stufe ersetzt nicht");
        assertTrue(player.pickaxe(3));
        assertEquals(3, player.pickaxe());
        player.slots(4, 2);
        assertEquals(4, player.swordSlot());
        assertEquals(2, player.pickaxeSlot());
    }

    @Test
    void goldVoteMehrheitSonstNormal() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        assertEquals(GoldVote.Rate.NORMAL, GoldVote.winner(Map.of()));
        assertEquals(GoldVote.Rate.HIGH, GoldVote.winner(Map.of(a, GoldVote.Rate.HIGH, b, GoldVote.Rate.HIGH,
                c, GoldVote.Rate.NONE)));
        assertEquals(GoldVote.Rate.NORMAL, GoldVote.winner(Map.of(a, GoldVote.Rate.HIGH, b, GoldVote.Rate.LOW)));
        assertEquals(List.of(0, 60, 30, 15), java.util.Arrays.stream(GoldVote.Rate.values())
                .map(GoldVote.Rate::seconds).toList());
    }

    @Test
    void abklingzeiten() {
        Cooldowns cooldowns = new Cooldowns();
        UUID player = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        cooldowns.start(player, "platform", Duration.ofSeconds(20), now);
        assertEquals(20, cooldowns.remaining(player, "platform", now));
        assertEquals(1, cooldowns.remaining(player, "platform", now.plusMillis(19_500)));
        assertEquals(0, cooldowns.remaining(player, "platform", now.plusSeconds(20)));
        assertEquals(0, cooldowns.remaining(player, "portal", now));
    }
}
