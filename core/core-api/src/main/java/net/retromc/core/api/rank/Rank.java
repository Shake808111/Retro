package net.retromc.core.api.rank;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Die einzige Rangtabelle des Netzwerks (Spec 01 §6.1; Spec 02 §6.12 übernimmt sie). Werte sind Standardwerte für
 * die LuckPerms-Gruppen ({@code meta.display}, {@code meta.color}, {@code meta.gradient}, {@code prefix.<weight>.*})
 * und das Seed-Skript; zur Laufzeit gilt LuckPerms-Meta.
 *
 * <p>Präfix und Name haben dieselbe Farbe. „X+“ in den Specs bedeutet Gewicht ≥ X (Spec 02 §6.12).
 */
public enum Rank {
    /** Bedeutung offen (Spec 01 O6): nur für unsichtbares Team, wird bis zur Klärung nicht vergeben. */
    SPEC(1000, "spec", "Spectator", "Spec", "§7", "§7", null),
    ADMIN(900, "admin", "Administrator", "Admin", "§4", "§4", null),
    SRDEV(850, "srdev", "Senior-Developer", "SrDev", "§c", "§c", null),
    DEV(800, "dev", "Developer", "Dev", "§c", "§c", null),
    SRCONTENT(750, "srcontent", "Senior-Content", "SrContent", "§c", "§c", null),
    CONTENT(700, "content", "Content", "Content", "§c", "§c", null),
    SRMOD(650, "srmod", "Senior-Moderator", "SrMod", "§c", "§c", null),
    MOD(600, "mod", "Moderator", "Mod", "§c", "§c", null),
    SUP(550, "sup", "Supporter", "Sup", "§c", "§c", null),
    BUILDER(500, "builder", "Builder", "Builder", "§2", "§2", null),
    /** Ära 18: Farbe §5 (Option „YouTuber“). */
    VIP(450, "vip", "VIP", "VIP", "§d", "§5", null),
    /** Name als Verlauf {@code #FF55FF→#55FFFF} (1.21+), Rückfallfarbe §d. */
    SUPREMIUM_PLUS(400, "supremiumplus", "Supremium+", "Suprem+", "§d", "§d", "#FF55FF:#55FFFF"),
    SUPREMIUM(350, "supremium", "Supremium", "Suprem", "§b", "§b", null),
    PREMIUM_PLUS(300, "premiumplus", "Premium Plus", "Premium+", "§e", "§e", null),
    /** Kein Präfix in Tab und Nametag. */
    PREMIUM(200, "premium", "Premium", null, "§6", "§6", null),
    /** Spielerfarbe §a, Ära 18 §8 (Spec 01 O7). */
    DEFAULT(0, "default", "Spieler", null, "§a", "§8", null);

    /** Trenner Präfix ↔ Name, Ära 26 (Spec 01 §6.1). */
    public static final String SEPARATOR = " §7| ";

    /** Trenner Präfix ↔ Name, Ära 18 ({@code legacyStyle}). */
    public static final String LEGACY_SEPARATOR = " §8┃ ";

    /** Ab diesem Gewicht gilt ein Rang als Team (Builder und höher). */
    public static final int TEAM_WEIGHT = 500;

    private final int weight;
    private final String group;
    private final String displayName;
    private final @Nullable String prefix;
    private final String color;
    private final String legacyStyleColor;
    private final @Nullable String gradient;

    Rank(int weight, String group, String displayName, @Nullable String prefix, String color,
            String legacyStyleColor, @Nullable String gradient) {
        this.weight = weight;
        this.group = group;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
        this.legacyStyleColor = legacyStyleColor;
        this.gradient = gradient;
    }

    /** LuckPerms-Gewicht 0–1000; bestimmt auch die Tab-Sortierung (Spec 02 §6.2). */
    public int weight() {
        return weight;
    }

    /** LuckPerms-Gruppe. */
    public String group() {
        return group;
    }

    /** Anzeigename (Scoreboard, {@code /rank}). */
    public String displayName() {
        return displayName;
    }

    /** Präfix in Tab und Nametag; {@code null} = kein Präfix. */
    public @Nullable String prefix() {
        return prefix;
    }

    /** Farbe von Präfix und Name als §-Code (Ära 26). */
    public String color() {
        return color;
    }

    /** Farbe bei {@code legacyStyle} (Ära 18, Spec 02 §6.12 Spalte LEGACY). */
    public String legacyStyleColor() {
        return legacyStyleColor;
    }

    /** Verlauf {@code #RRGGBB:#RRGGBB} oder {@code null}; ohne Verlauf gilt {@link #color()}. */
    public @Nullable String gradient() {
        return gradient;
    }

    public boolean isTeam() {
        return weight >= TEAM_WEIGHT;
    }

    public boolean isAtLeast(Rank other) {
        return weight >= other.weight;
    }

    /** Zahlen-Vorteile nach Spec 01 §6.2. Spalte „VIP/Team“: Team-Ränge erhalten ∞ bei {@code /joinme}. */
    public RankLimits defaultLimits() {
        return switch (this) {
            case DEFAULT -> new RankLimits(3, 250, 0, 0, 0);
            case PREMIUM -> new RankLimits(10, 500, 2, 0, 0);
            case PREMIUM_PLUS -> new RankLimits(26, 500, 2, 0, 0);
            case SUPREMIUM -> new RankLimits(64, 1000, 5, 3, 10);
            case SUPREMIUM_PLUS -> new RankLimits(64, 1000, 5, 3, 30);
            case VIP -> new RankLimits(64, 1000, 5, 3, RankLimits.UNLIMITED);
            case BUILDER, SUP, MOD, SRMOD, CONTENT, SRCONTENT, DEV, SRDEV, ADMIN, SPEC ->
                    new RankLimits(64, 1000, 5, RankLimits.UNLIMITED, RankLimits.UNLIMITED);
        };
    }

    /** Schalter-Vorteile nach Spec 01 §6.2; Coin-Boost nur Supremium und Supremium+ (Spec 02 §6.11). */
    public RankFlags defaultFlags() {
        return switch (this) {
            case DEFAULT -> new RankFlags(false, false, false, false, 0, false, false, false, LobbyMovement.NONE);
            case PREMIUM -> new RankFlags(false, false, true, false, 0, false, true, false, LobbyMovement.DOUBLE_JUMP);
            case PREMIUM_PLUS -> new RankFlags(true, false, true, false, 0, false, true, false, LobbyMovement.FLY);
            case SUPREMIUM -> new RankFlags(true, true, true, true, 1, true, true, true, LobbyMovement.FLY);
            case SUPREMIUM_PLUS -> new RankFlags(true, true, true, true, 2, true, true, true, LobbyMovement.FLY);
            case VIP, BUILDER, SUP, MOD, SRMOD, CONTENT, SRCONTENT, DEV, SRDEV, ADMIN, SPEC ->
                    new RankFlags(true, true, true, true, 3, true, true, false, LobbyMovement.FLY);
        };
    }

    /** Rang zur LuckPerms-Gruppe (Groß-/Kleinschreibung egal). */
    public static Optional<Rank> byGroup(String group) {
        String key = group.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(rank -> rank.group.equals(key)).findFirst();
    }

    /** Höchster Rang, dessen Gewicht {@code weight} nicht übersteigt (unbekannte Zwischengewichte). */
    public static Rank forWeight(int weight) {
        return Arrays.stream(values()).filter(rank -> rank.weight <= weight).findFirst().orElse(DEFAULT);
    }
}
