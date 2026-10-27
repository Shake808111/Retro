package net.retromc.core.api.rank;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Anzeige-Sicht eines Rangs für Chat, Tab, Nametag und Scoreboard (Spec 02 §1, §6.12; Daten aus Spec 01 §6.1).
 *
 * @param group LuckPerms-Gruppe
 * @param displayName Anzeigename
 * @param prefix Tab-Präfix oder {@code null}
 * @param color §-Farbe MODERN
 * @param legacyStyleColor §-Farbe LEGACY ({@code ui.era}/{@code legacyStyle})
 * @param gradient Verlauf {@code #RRGGBB:#RRGGBB} (nur MODERN) oder {@code null}
 * @param weight Gewicht (Tab-Sortierung {@code order = weight}, Spec 02 §6.2)
 * @param booster Coin-Boost-Rang (Spec 02 §6.11)
 * @param coloredGg farbiges GG (Spec 02 §6.1 Schritt 4)
 */
public record RankDisplay(
        String group,
        String displayName,
        @Nullable String prefix,
        String color,
        String legacyStyleColor,
        @Nullable String gradient,
        int weight,
        boolean booster,
        boolean coloredGg) {

    public RankDisplay {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(legacyStyleColor, "legacyStyleColor");
    }

    public static RankDisplay of(Rank rank) {
        return new RankDisplay(rank.group(), rank.displayName(), rank.prefix(), rank.color(),
                rank.legacyStyleColor(), rank.gradient(), rank.weight(), rank.defaultFlags().booster(),
                rank.defaultFlags().coloredGg());
    }

    /** Darstellung genickter Spieler: Rang Premium (§6), ohne Clan-Tag (Spec 01 §6.11, O20). */
    public static RankDisplay nicked() {
        return of(Rank.PREMIUM);
    }

    /** Namensfarbe je Ära; der Verlauf wird nur MODERN genutzt (Rückfall = {@link #color()}). */
    public String nameColor(boolean legacyStyle) {
        return legacyStyle ? legacyStyleColor : color;
    }

    /** Trenner zwischen Präfix und Name je Ära. */
    public static String separator(boolean legacyStyle) {
        return legacyStyle ? Rank.LEGACY_SEPARATOR : Rank.SEPARATOR;
    }
}
