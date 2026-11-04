package net.retromc.core.api.social;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Pflege von {@code core.nick_pool} (Spec 01 §6.11 Nr. 3 und 7, Admin-Befehl {@code /nickpool}): erzeugt realistische
 * Zufallsnamen, prüft sie gegen bekannte Spieler, Wortfilter und optional die Mojang-Namens-API und kombiniert sie
 * zufällig mit Mojang-signierten Skins ({@code core.nick_skin}). Die Vergabe an Spieler macht der
 * {@link NickService}.
 */
public interface NickPoolService {

    /** Höchstens so viele Namen je {@code /nickpool fill}. */
    int MAX_FILL = 500;

    /** Füllt den Pool um bis zu {@code count} neue Namen (holt fehlende Skins vorher nach). */
    CompletableFuture<Fill> fill(int count);

    /** Pool-Namen alphabetisch, ab {@code offset}, höchstens {@code limit}. */
    CompletableFuture<List<Entry>> list(int offset, int limit);

    /** Name sperren (wird nicht mehr vergeben; ein laufender Nick bleibt bis zur Freigabe); {@code false} = unbekannt. */
    CompletableFuture<Boolean> disable(String name);

    CompletableFuture<Stats> stats();

    /** Zustand eines Pool-Namens für {@code /nickpool list}. */
    enum State {
        FREE,
        IN_USE,
        DISABLED
    }

    /** Ein Pool-Name. */
    record Entry(String name, State state, String source, Instant createdAt) {

        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /**
     * Zahlen für {@code /nickpool stats} und die Monitoring-Warnung ({@link NickService#POOL_WARN_FREE}).
     *
     * @param free aktiv, nicht vergeben und nicht als echter Spieler online
     */
    record Stats(int total, int enabled, int free, int inUse, int skins) {

        public boolean low() {
            return free < NickService.POOL_WARN_FREE;
        }
    }

    /** Ausgang von {@link #fill(int)}. */
    enum FillStatus {
        /** Gelaufen (eventuell weniger Namen als verlangt, siehe Zähler). */
        DONE,
        /** Abgebrochen, weil Mojang das Anfragelimit meldet (HTTP 429); bis dahin gefundene Namen sind gespeichert. */
        RATE_LIMITED,
        /** Kein Skin vorhanden oder abrufbar. */
        NO_SKINS,
        /** Es läuft schon ein Füllvorgang. */
        BUSY
    }

    /**
     * Ergebnis von {@link #fill(int)}.
     *
     * @param known verworfen: echter Spieler (online, Profil, Namenshistorie) oder schon im Pool
     * @param filtered verworfen: Wortfilter
     * @param taken verworfen: bei Mojang vergeben oder nicht prüfbar
     */
    record Fill(FillStatus status, int requested, int added, int skinsAdded, int skins, int known, int filtered,
            int taken) {

        public Fill {
            Objects.requireNonNull(status, "status");
        }

        public static Fill of(FillStatus status, int requested) {
            return new Fill(status, requested, 0, 0, 0, 0, 0, 0);
        }
    }
}
