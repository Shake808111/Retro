package net.retromc.core.api.social;

import java.util.Objects;
import java.util.UUID;

/**
 * Normalisiertes Freundespaar für die Tabelle {@code social.friendship (a, b)} mit
 * {@code CHECK (a < b)} (Notiz 07, Abschnitt 4, Schema-Skizze).
 *
 * <p>Wichtig: PostgreSQL vergleicht UUIDs byteweise ohne Vorzeichen, {@link UUID#compareTo(UUID)}
 * vergleicht dagegen die beiden {@code long}-Hälften MIT Vorzeichen. Deshalb wird hier ohne
 * Vorzeichen verglichen, sonst würde die CHECK-Bedingung bei manchen Paaren verletzt.
 */
public record FriendPair(UUID a, UUID b) {

    public FriendPair {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (compareLikePostgres(a, b) >= 0) {
            throw new IllegalArgumentException("a muss (wie in PostgreSQL) kleiner als b sein; FriendPair.of(...) verwenden");
        }
    }

    /** Erstellt das Paar in der Reihenfolge, die PostgreSQL für {@code a < b} erwartet. */
    public static FriendPair of(UUID x, UUID y) {
        int cmp = compareLikePostgres(x, y);
        if (cmp == 0) {
            throw new IllegalArgumentException("Ein Spieler kann nicht mit sich selbst befreundet sein");
        }
        return cmp < 0 ? new FriendPair(x, y) : new FriendPair(y, x);
    }

    /** Vergleich wie PostgreSQL (byteweise, ohne Vorzeichen). */
    public static int compareLikePostgres(UUID x, UUID y) {
        int high = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
    }
}
