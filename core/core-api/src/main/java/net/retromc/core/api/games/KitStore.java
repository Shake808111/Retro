package net.retromc.core.api.games;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kit-Besitz und -Auswahl je Modus ({@code games.kit_owned}, {@code games.kit_selected}; Spec 05 §2.1, genutzt von
 * SkyWars Spec 04 §5.9/§6.2.5). Der Kauf bucht die Coins über das Journal ({@code economy.ledger}) mit dem
 * Idempotenz-Schlüssel {@link #idemKey} und legt danach den Besitz an – ein Doppelklick bucht genau einmal.
 */
public interface KitStore {

    /** Auswahl „zufälliges Kit“ ({@code kit_selected = random}). */
    String RANDOM = "random";

    /** Ausgang eines Kaufs. */
    enum Status {
        /** Gekauft (Coins gebucht, Besitz angelegt; auch bei wiederholtem Aufruf nach erfolgter Buchung). */
        BOUGHT,
        /** Besaß das Kit schon, nichts gebucht. */
        OWNED,
        /** Zu wenig Coins, nichts gebucht. */
        INSUFFICIENT
    }

    /**
     * Ergebnis eines Kaufs.
     *
     * @param balance Coins nach dem Kauf bzw. aktueller Stand
     * @param missing fehlende Coins bei {@link Status#INSUFFICIENT}, sonst 0
     */
    record Purchase(Status status, long balance, long missing) {

        public Purchase {
            Objects.requireNonNull(status, "status");
        }
    }

    /** Gekaufte Kit-IDs eines Spielers in einem Modus. */
    CompletableFuture<Set<String>> owned(UUID player, String mode);

    /** Zuletzt gewähltes Kit oder leer. */
    CompletableFuture<Optional<String>> selected(UUID player, String mode);

    /** Speichert die Auswahl ({@link #RANDOM} erlaubt). */
    CompletableFuture<Void> select(UUID player, String mode, String kitId);

    /** Kauft ein Kit für {@code price} Coins (idempotent je Spieler, Modus und Kit). */
    CompletableFuture<Purchase> buy(UUID player, String mode, String kitId, long price);

    /** Idempotenz-Schlüssel im Coin-Journal: {@code kit:{mode}:{kitId}:{uuid}} (Spec 04 §5.9). */
    static String idemKey(UUID player, String mode, String kitId) {
        return "kit:" + mode + ":" + kitId + ":" + player;
    }
}
