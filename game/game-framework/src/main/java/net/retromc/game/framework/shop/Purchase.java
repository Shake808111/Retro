package net.retromc.game.framework.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Bezahlen mit Items aus dem Inventar (Spec 04 §6.1.6 Kauf-Ablauf, U4), ohne Bukkit: Stapel werden von vorn nach
 * hinten geleert, bis der Preis erreicht ist.
 */
public final class Purchase {

    /**
     * Ergebnis.
     *
     * @param paid      {@code true}, wenn genug da war
     * @param missing   fehlender Betrag (0 bei Erfolg)
     * @param remaining neue Stapelgrößen in derselben Reihenfolge (bei Fehlschlag unverändert)
     */
    public record Result(boolean paid, int missing, List<Integer> remaining) {

        public Result {
            remaining = List.copyOf(remaining);
        }
    }

    private Purchase() {
    }

    /** Zieht {@code price} von den Stapeln {@code stacks} ab. */
    public static Result tryBuy(List<Integer> stacks, int price) {
        if (price < 0) {
            throw new IllegalArgumentException("Preis negativ: " + price);
        }
        int available = stacks.stream().mapToInt(Integer::intValue).sum();
        if (available < price) {
            return new Result(false, price - available, stacks);
        }
        List<Integer> remaining = new ArrayList<>(stacks);
        int open = price;
        for (int i = 0; i < remaining.size() && open > 0; i++) {
            int take = Math.min(open, remaining.get(i));
            remaining.set(i, remaining.get(i) - take);
            open -= take;
        }
        return new Result(true, 0, remaining);
    }
}
