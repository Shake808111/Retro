package net.retromc.skywars;

import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Einstellungen aus {@code config.yml}, Abschnitt {@code skywars} (Spec 04 §8.3). Werte ohne Beleg sind Annahmen der
 * Spec (§6.2, §10) und hier änderbar.
 *
 * @param classic           Classic-Preset {@code skywars_classic} (Spec 04 §6.2.7)
 * @param cageSeconds       Käfig-Countdown nach dem Teleport (3-2-1)
 * @param protectionSeconds Schutzzeit/Aufbauphase (30 s, B)
 * @param borderDelay       Border-Start nach Kampfbeginn (600 s, B)
 * @param borderTwoTeams    Border-Start, wenn nur noch 2 Teams leben (60 s, B)
 * @param borderShrink      Dauer des Schrumpfens (180 s, AN)
 * @param borderEndRadius   End-Radius um die Mitte (10, AN)
 * @param borderDamage      Schaden je Sekunde außerhalb (1, AN)
 * @param maxMinutes        Rundenhöchstdauer bis Unentschieden (Sicherheitsnetz, AN)
 * @param labelColon        Board-Option {@code board.labelColon} ({@code Map:} statt {@code Map})
 * @param rewards           XP und Coins (§6.2.3)
 * @param levelColors       Farbe des Modus-Levels ab Level (AN), sortiert
 * @param starFrom          ab diesem Level {@code ✪} hinter der Zahl (300, AN)
 */
public record SkyWarsConfig(boolean classic, int cageSeconds, int protectionSeconds, int borderDelay,
        int borderTwoTeams, int borderShrink, int borderEndRadius, double borderDamage, int maxMinutes,
        boolean labelColon, Rewards rewards, Map<Integer, String> levelColors, int starFrom) {

    /**
     * Belohnungen (Spec 04 §6.2.3).
     *
     * @param kill          XP je Kill (+20, B)
     * @param win           XP für den Sieg (+50, AN)
     * @param surviveSeconds 1 XP je so viele Sekunden Überleben (15, AN)
     * @param coinFactor    Coins = Faktor × Runden-XP …
     * @param coinsPerKill  … + Coins je Kill (2 × XP + 10 je Kill, AN)
     */
    public record Rewards(int kill, int win, int surviveSeconds, int coinFactor, int coinsPerKill) {

        public static final Rewards DEFAULT = new Rewards(20, 50, 15, 2, 10);

        /** XP am Rundenende: Sieg + 1 XP je {@code surviveSeconds} Überleben. */
        public int endXp(boolean won, long survivedSeconds) {
            return (won ? win : 0) + (int) (Math.max(0, survivedSeconds) / Math.max(1, surviveSeconds));
        }

        /** Basis-Coins für {@code payoutRound}. */
        public long coins(int roundXp, int kills) {
            return (long) coinFactor * Math.max(0, roundXp) + (long) coinsPerKill * Math.max(0, kills);
        }
    }

    public SkyWarsConfig {
        levelColors = Map.copyOf(new TreeMap<>(levelColors));
        if (cageSeconds < 1 || protectionSeconds < 0 || borderDelay < 1 || borderTwoTeams < 1 || borderShrink < 1) {
            throw new IllegalArgumentException("SkyWars-Zeiten ungültig");
        }
    }

    /** Standardwerte der Spec. */
    public static final SkyWarsConfig DEFAULT = new SkyWarsConfig(false, 3, 30, 600, 60, 180, 10, 1.0, 30, false,
            Rewards.DEFAULT, Map.of(1, "§7", 25, "§f", 50, "§a", 100, "§e", 150, "§6", 200, "§c", 250, "§d",
                    300, "§b"), 300);

    public static SkyWarsConfig read(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("skywars");
        if (section == null) {
            return DEFAULT;
        }
        ConfigurationSection border = section.getConfigurationSection("border");
        ConfigurationSection rewards = section.getConfigurationSection("rewards");
        Rewards values = rewards == null ? Rewards.DEFAULT : new Rewards(rewards.getInt("kill", 20),
                rewards.getInt("win", 50), rewards.getInt("survive-seconds", 15), rewards.getInt("coin-factor", 2),
                rewards.getInt("coins-per-kill", 10));
        Map<Integer, String> colors = new TreeMap<>();
        ConfigurationSection levels = section.getConfigurationSection("level-colors");
        if (levels != null) {
            levels.getKeys(false).forEach(key -> colors.put(Integer.parseInt(key), levels.getString(key, "§7")));
        }
        return new SkyWarsConfig("classic".equalsIgnoreCase(section.getString("preset", "modern")),
                section.getInt("cage-seconds", 3), section.getInt("protection-seconds", 30),
                border == null ? 600 : border.getInt("delay-seconds", 600),
                border == null ? 60 : border.getInt("two-teams-seconds", 60),
                border == null ? 180 : border.getInt("shrink-seconds", 180),
                border == null ? 10 : border.getInt("end-radius", 10),
                border == null ? 1.0 : border.getDouble("damage", 1.0), section.getInt("max-minutes", 30),
                section.getBoolean("board-label-colon", false), values,
                colors.isEmpty() ? DEFAULT.levelColors() : colors, section.getInt("star-from", 300));
    }

    /** Statistik-Modus. */
    public String modeId() {
        return classic ? "skywars_classic" : "skywars";
    }
}
