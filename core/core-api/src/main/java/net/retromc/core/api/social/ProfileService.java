package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Profil-Cache {@code profile:{uuid}} und Spielerdaten {@code core.player} (Spec 01 §2.3, §6.3, §8.1).
 * Genutzt von allen Paper-Plugins (Chat, Tab, Nametag) und den Social-Befehlen (Namensauflösung).
 */
public interface ProfileService {

    /** Profil aus dem Cache, bei Bedarf aus der Datenbank neu aufgebaut; leer bei unbekanntem Spieler. */
    CompletableFuture<Optional<Profile>> profile(UUID player);

    /** Profile mehrerer Spieler; unbekannte fehlen im Ergebnis. */
    CompletableFuture<Map<UUID, Profile>> profiles(Collection<UUID> players);

    /** UUID zu einem echten Spielernamen ({@code name:{lower}}, sonst {@code core.player.name_lower}). */
    CompletableFuture<Optional<UUID>> uuidOf(String name);

    /** Zuletzt bekannter Name. */
    CompletableFuture<Optional<String>> nameOf(UUID player);

    /** Login verbuchen und Profil schreiben (Spec 01 §6.3 Schritt 4). */
    CompletableFuture<Profile> recordLogin(PlayerLogin login);

    /** Logout verbuchen: {@code last_quit}, {@code playtime_s += sessionSeconds} (Spec 01 §6.3 Schritt 7). */
    CompletableFuture<Void> recordQuit(UUID player, Instant quitAt, long sessionSeconds);

    /** Zwischenspeichern der Spielzeit alle 5 min (Spec 02 §6.3). */
    CompletableFuture<Void> addPlaytime(UUID player, long seconds);

    /**
     * Profil-Cache verwerfen und neu aufbauen, danach {@code PROFILE_INVALIDATE} senden.
     *
     * @param fields geänderte Felder (Konstanten aus {@link Profile}); leer = alle
     */
    CompletableFuture<Void> invalidate(UUID player, Collection<String> fields);
}
