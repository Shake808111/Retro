package net.retromc.paper.core.stats;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.ModeKey;

/**
 * Modus des Servers für {@code /stats}, {@code /top} und {@code /resetstats} (Spec 02 §3): Wartelobby und Runde
 * eines Spielservers ({@code bedwars-4x4} → {@code bedwars}, {@code bingo-x2} → {@code bingo_x2}) oder eine
 * Modus-Lobby (Kategorie {@code LOBBY} mit Template {@code lobby-{mode}}, Spec 08 §6.5, K12). Hauptlobby und
 * Dauerserver haben keinen Modus ({@code /stats <Modus> [Spieler]}).
 *
 * @param mode Modus oder leer
 * @param modeLobby Lobby dieses Modus ({@code /resetstats} erlaubt)
 */
public record ServerMode(Optional<ModeKey> mode, boolean modeLobby) {

    public static final ServerMode NONE = new ServerMode(Optional.empty(), false);

    /**
     * Ermittelt den Modus aus Template und Kategorie.
     *
     * @param known Modus-Schlüssel des Katalogs
     */
    public static ServerMode resolve(TemplateId template, ServerCategory category, Predicate<ModeKey> known) {
        String id = template.value().toLowerCase(Locale.ROOT);
        return switch (category) {
            case LOBBY -> id.startsWith("lobby-")
                    ? candidate(id.substring("lobby-".length()), known).map(mode -> new ServerMode(Optional.of(mode),
                            true)).orElse(NONE)
                    : NONE;
            case GAME -> candidate(id, known).map(mode -> new ServerMode(Optional.of(mode), false)).orElse(NONE);
            case PERSISTENT -> NONE;
        };
    }

    /** Erst das ganze Template ({@code bingo-x2} → {@code bingo_x2}), dann der Teil vor dem ersten Bindestrich. */
    private static Optional<ModeKey> candidate(String id, Predicate<ModeKey> known) {
        String whole = id.replace('-', '_');
        if (ModeKey.PATTERN.matcher(whole).matches() && known.test(ModeKey.of(whole))) {
            return Optional.of(ModeKey.of(whole));
        }
        int dash = id.indexOf('-');
        String head = dash < 0 ? id : id.substring(0, dash);
        if (ModeKey.PATTERN.matcher(head).matches() && known.test(ModeKey.of(head))) {
            return Optional.of(ModeKey.of(head));
        }
        return Optional.empty();
    }
}
