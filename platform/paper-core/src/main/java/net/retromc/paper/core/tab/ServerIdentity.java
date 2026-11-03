package net.retromc.paper.core.tab;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.platform.common.text.ColorText;

/**
 * Anzeigename und Modusfarbe des Servers für Tab-Header Zeile 1 {@code §e{brand}.net {modeColor}{serverDisplay}}
 * (Spec 02 §4.4, Spec 01 §4.2 {@code modeColor}-Registry, Spec 08 §4 {@code serverDisplay}). Lobbys:
 * {@code {displayName}-{n}} ({@code lobby} ohne Zahl = {@code Lobby-1}); Spiel- und Dauerserver: {@code {displayName}}.
 *
 * @param display {@code serverDisplay}
 * @param color Modusfarbe als §-Code
 */
public record ServerIdentity(String display, String color) {

    public ServerIdentity {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(color, "color");
    }

    /**
     * Ermittelt Name und Farbe. Farben: Modus aus {@code stats.mode.color} (BedWars §c, SkyWars §b, TTT §c, Duels §9),
     * CityBuild §6, SMP §d, Silent Lobby §c, Lobby §a, sonst §a.
     *
     * @param mode Modus des Servers (Modus-Lobby, Spielserver)
     * @param modeName Anzeigename eines Modus ({@code mode.<key>})
     */
    public static ServerIdentity of(String serverName, TemplateId template, ServerCategory category,
            Optional<StatsMode> mode, Function<StatsMode, String> modeName) {
        String id = template.value();
        int number = TemplateId.trailingNumber(serverName).orElse(1);
        String modeColor = mode.map(found -> ColorText.legacyCode(found.color())).orElse("§a");
        return switch (category) {
            case LOBBY -> switch (id) {
                case "lobby" -> new ServerIdentity("Lobby-" + number, "§a");
                case "premiumlobby" -> new ServerIdentity("Premium-Lobby-" + number, "§a");
                case "silentlobby" -> new ServerIdentity("Silent-Lobby-" + number, "§c");
                default -> new ServerIdentity(mode.map(found -> modeName.apply(found) + "-Lobby")
                        .orElse(capitalized(id)) + "-" + number, modeColor);
            };
            case GAME -> new ServerIdentity(mode.map(modeName).orElse(capitalized(id)), modeColor);
            case PERSISTENT -> {
                if (id.startsWith("cb")) {
                    yield new ServerIdentity("CityBuild", "§6");
                }
                if (id.startsWith("smp")) {
                    yield new ServerIdentity("SMP", "§d");
                }
                yield new ServerIdentity(capitalized(id), "§a");
            }
        };
    }

    private static String capitalized(String id) {
        return id.isEmpty() ? id : id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }
}
