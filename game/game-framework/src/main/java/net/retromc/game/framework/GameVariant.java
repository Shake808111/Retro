package net.retromc.game.framework;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spielvariante {@code {teams}x{teamSize}} (Spec 03 §6.5, §6.6, §8.1).
 *
 * @param key        Varianten-ID, z. B. {@code 8x4}
 * @param teams      Anzahl Teams
 * @param teamSize   Spieler je Team
 * @param minPlayers Mindestspieler; {@code 0} = {@code ceil(maxPlayers / 2)} („halb voll“)
 * @param privateMax Höchstzahl in Privatrunden ({@code /pr max}); {@code 0} = {@link #maxPlayers()}
 */
public record GameVariant(String key, int teams, int teamSize, int minPlayers, int privateMax) {

    private static final Pattern TEAMS_X_SIZE = Pattern.compile("(\\d{1,2})x(\\d{1,2})");

    public GameVariant {
        Objects.requireNonNull(key, "key");
        if (teams < 1 || teamSize < 1) {
            throw new IllegalArgumentException("teams und teamSize müssen ≥ 1 sein: " + key);
        }
        if (minPlayers < 0 || privateMax < 0) {
            throw new IllegalArgumentException("minPlayers und privateMax dürfen nicht negativ sein: " + key);
        }
        if (minPlayers > teams * teamSize) {
            throw new IllegalArgumentException("minPlayers > maxPlayers: " + key);
        }
    }

    /** Variante aus dem Schlüssel {@code NxM} ohne Overrides. */
    public static GameVariant parse(String key) {
        Matcher matcher = TEAMS_X_SIZE.matcher(Objects.requireNonNull(key, "key"));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Variante muss {teams}x{teamSize} sein: " + key);
        }
        return new GameVariant(key, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), 0, 0);
    }

    /** {@code teams × teamSize} (Spec 03 §6.5). */
    public int maxPlayers() {
        return teams * teamSize;
    }

    /** Mindestspieler: Override oder {@code ceil(maxPlayers / 2)} (Spec 03 §6.5, §10 #7). */
    public int effectiveMinPlayers() {
        return minPlayers > 0 ? minPlayers : (maxPlayers() + 1) / 2;
    }

    /** Höchstzahl in Privatrunden. */
    public int effectivePrivateMax() {
        return privateMax > 0 ? privateMax : maxPlayers();
    }

    /** Ob es Teams mit mehr als einem Spieler gibt (Teamwahl, Teamchat). */
    public boolean teamPlay() {
        return teamSize > 1;
    }

    /** Anzeige im Wartelobby-Board {@code {teams}x{teamSize}}. */
    public String display() {
        return teams + "x" + teamSize;
    }
}
