package net.retromc.core.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.stats.CoinPayout;

/**
 * Coin-Boost am Rundenende (Spec 02 §6.11, O2): Jeder Spieler der Runde mit Boost-Rang ist ein Booster. Bonus =
 * {@code floor(base × min(0,1·n, 0,4))}, {@code n} = Booster der Runde inklusive Empfänger; die Namensliste nennt
 * die Booster ohne den Empfänger (1 → {@code A}, 2 → {@code A, B}, ≥ 3 → {@code A, B und {n−2} weiteren}).
 */
public final class CoinBoost {

    /** Prozent je Booster. */
    public static final int PERCENT_PER_BOOSTER = 10;
    /** Deckel in Prozent. */
    public static final int MAX_PERCENT = 40;

    private CoinBoost() {
    }

    /** Bonus für {@code base} bei {@code boosters} Boostern (ganzzahlig abgerundet). */
    public static long bonus(long base, int boosters) {
        if (base < 0 || boosters < 0) {
            throw new IllegalArgumentException("Negative Werte");
        }
        long percent = Math.min((long) PERCENT_PER_BOOSTER * boosters, MAX_PERCENT);
        return Math.multiplyExact(base, percent) / 100;
    }

    /**
     * Auszahlung eines Spielers.
     *
     * @param roundBoosters Booster der Runde in Anzeigereihenfolge (darf den Empfänger enthalten)
     */
    public static CoinPayout payout(UUID player, long base, Collection<UUID> roundBoosters) {
        Objects.requireNonNull(player, "player");
        List<UUID> boosters = roundBoosters.stream().distinct().toList();
        List<UUID> others = new ArrayList<>(boosters);
        others.remove(player);
        return new CoinPayout(player, base, bonus(base, boosters.size()), boosters.size(), others);
    }

    /** Form der Namensliste (Spec 02 §6.11 „Anzeige“). */
    public record Names(String first, String second, int more) {

        /**
         * Ordnet die Namen: 1 Name → nur {@code first}; 2 → {@code first, second}; ≥ 3 → {@code first, second} und
         * {@code more = n − 2} weitere.
         */
        public static Names of(List<String> names) {
            if (names.isEmpty()) {
                throw new IllegalArgumentException("Mindestens ein Name");
            }
            String second = names.size() >= 2 ? names.get(1) : "";
            return new Names(names.getFirst(), second, Math.max(0, names.size() - 2));
        }

        /** Anzahl der Namen insgesamt. */
        public int count() {
            return second.isEmpty() ? 1 : 2 + more;
        }

        /** Textschlüssel: {@code coins.names.single}, {@code .pair} oder {@code .more}. */
        public String key() {
            return switch (count()) {
                case 1 -> "coins.names.single";
                case 2 -> "coins.names.pair";
                default -> "coins.names.more";
            };
        }

        /** Klartext ohne Farben (Tests, Logs): {@code A}, {@code A, B}, {@code A, B und 5 weiteren}. */
        public String plain() {
            return switch (count()) {
                case 1 -> first;
                case 2 -> first + ", " + second;
                default -> first + ", " + second + " und " + more + " weiteren";
            };
        }
    }
}
