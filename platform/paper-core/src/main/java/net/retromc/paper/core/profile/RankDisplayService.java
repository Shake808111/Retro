package net.retromc.paper.core.profile;

import java.util.Arrays;
import java.util.UUID;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.rank.RankInfo;

/**
 * Rang-Anzeige eines Spielers (Spec 02 §8.1 {@code RankDisplayService}): Farbe, Präfix, Gewicht, Verlauf und
 * Boost/GG aus LuckPerms-Meta (Spec 01 §6.1). Unbekannte Spieler gelten als {@link Rank#DEFAULT}.
 */
public interface RankDisplayService {

    RankDisplay of(UUID player);

    /** Anzeige zu einem Rang aus LuckPerms; die LEGACY-Farbe kommt aus der Rangtabelle (Spec 02 §6.12). */
    static RankDisplay of(RankInfo rank) {
        String legacyStyle = Arrays.stream(Rank.values()).filter(known -> known.group().equals(rank.id()))
                .findFirst().map(Rank::legacyStyleColor).orElse(rank.legacyColor());
        return new RankDisplay(rank.id(), rank.displayName(), rank.prefix(), rank.legacyColor(), legacyStyle,
                rank.gradient(), rank.weight(), rank.flags().booster(), rank.flags().coloredGg());
    }
}
