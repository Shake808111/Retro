package net.retromc.paper.core.stats;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.i18n.Messages;

/**
 * Modus-Katalog ({@code stats.mode}, Spec 02 §6.7) als Momentaufnahme für den Haupt-Thread: Chat-Präfixe, Tab-Farben,
 * Befehlsargumente und Anzeigen brauchen ihn synchron. Geladen beim Start, neu mit {@link #reload()}.
 */
public final class ModeCatalog {

    private final Supplier<StatsStore> store;
    private volatile List<StatsMode> modes = List.of();

    public ModeCatalog(Supplier<StatsStore> store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Katalog fest vorgeben (Tests, Adapter). */
    public static ModeCatalog of(List<StatsMode> modes) {
        ModeCatalog catalog = new ModeCatalog(() -> {
            throw new IllegalStateException("kein StatsStore");
        });
        catalog.modes = List.copyOf(modes);
        return catalog;
    }

    public CompletableFuture<List<StatsMode>> reload() {
        return store.get().modes().thenApply(loaded -> {
            modes = List.copyOf(loaded);
            return modes;
        });
    }

    /** Alle Modi nach {@code id}. */
    public List<StatsMode> all() {
        return modes;
    }

    public Optional<StatsMode> mode(ModeKey key) {
        return modes.stream().filter(mode -> mode.key().equals(key)).findFirst();
    }

    /**
     * Modus zu einer Eingabe: Schlüssel ({@code bedwars}, {@code bingo_x1}) oder deutscher Anzeigename
     * ({@code BedWars}, {@code Bingo x1}), Groß-/Kleinschreibung egal.
     */
    public Optional<StatsMode> find(String input) {
        String wanted = input.strip().toLowerCase(Locale.ROOT);
        return modes.stream().filter(mode -> mode.key().key().equals(wanted)
                || Messages.plain(Messages.DEFAULT_LOCALE, mode.i18nKey(), Map.of()).toLowerCase(Locale.ROOT)
                        .equals(wanted)).findFirst();
    }
}
