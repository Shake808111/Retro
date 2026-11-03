package net.retromc.paper.core.profile;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Profile der verbundenen Spieler dieses Servers (Spec 02 §2.2, §6.3): beim Join geladen, bei {@code PROFILE_CHANGED},
 * {@code PROFILE_INVALIDATE}, Social-Ereignissen und Rangänderungen neu geladen. Danach folgen
 * {@code ProfileLoadedEvent} bzw. {@code ProfileUpdatedEvent} auf dem Haupt-Thread.
 */
public interface PlayerProfiles {

    /** Geladenes Profil eines verbundenen Spielers; leer, solange es lädt. */
    Optional<ProfileView> view(UUID player);

    /**
     * Lädt das Profil neu, falls der Spieler hier verbunden ist.
     *
     * @param fields geänderte Felder ({@code Profile}-Konstanten); leer = alle
     */
    CompletableFuture<Optional<ProfileView>> refresh(UUID player, Collection<String> fields);
}
