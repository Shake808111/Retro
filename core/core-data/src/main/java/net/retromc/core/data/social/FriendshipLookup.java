package net.retromc.core.data.social;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.social.FriendPair;
import net.retromc.core.api.social.FriendRequest;
import net.retromc.core.api.social.FriendResult;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.FriendEntry;
import net.retromc.core.api.social.JumpResult;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/**
 * Einfache, nur lesende Standard-Implementierung von {@link FriendService}: beantwortet {@link #areFriends} aus
 * {@code social.friendship} (genutzt von {@code /msg} mit {@code msg_from = Freunde} und Nick-Adressierung,
 * Spec 01 §6.7). Das Freunde-System selbst (Anfragen, Listen, Favoriten, Nachspringen) stellt das Netzwerk über
 * einen Adapter bereit, der diesen Standard per {@code ServiceRegistry.register(FriendService.class, …)} ersetzt;
 * bis dahin melden die übrigen Methoden {@link UnsupportedOperationException}.
 */
public final class FriendshipLookup implements FriendService {

    private final AsyncDatabase database;

    public FriendshipLookup(AsyncDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public CompletableFuture<Boolean> areFriends(UUID a, UUID b) {
        if (a.equals(b)) {
            return CompletableFuture.completedFuture(false);
        }
        FriendPair pair = FriendPair.of(a, b);
        return database.query(connection -> Jdbc.count(connection,
                "SELECT count(*) FROM social.friendship WHERE a = ? AND b = ?", pair.a(), pair.b()) > 0);
    }

    private static <T> CompletableFuture<T> unsupported() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Freunde verwaltet das Freunde-System des Netzwerks (FriendService-Adapter)"));
    }

    @Override
    public CompletableFuture<OnlineCount> counts(UUID player) {
        return unsupported();
    }

    @Override
    public CompletableFuture<List<FriendEntry>> list(UUID player) {
        return unsupported();
    }

    @Override
    public CompletableFuture<FriendResult> request(UUID sender, UUID target) {
        return unsupported();
    }

    @Override
    public CompletableFuture<FriendResult> accept(UUID receiver, UUID sender) {
        return unsupported();
    }

    @Override
    public CompletableFuture<FriendResult> deny(UUID receiver, UUID sender) {
        return unsupported();
    }

    @Override
    public CompletableFuture<Integer> acceptAll(UUID receiver) {
        return unsupported();
    }

    @Override
    public CompletableFuture<Integer> denyAll(UUID receiver) {
        return unsupported();
    }

    @Override
    public CompletableFuture<List<FriendRequest>> requests(UUID receiver) {
        return unsupported();
    }

    @Override
    public CompletableFuture<FriendResult> remove(UUID player, UUID friend) {
        return unsupported();
    }

    @Override
    public CompletableFuture<Integer> clear(UUID player) {
        return unsupported();
    }

    @Override
    public CompletableFuture<Boolean> favorite(UUID owner, UUID friend) {
        return unsupported();
    }

    @Override
    public CompletableFuture<JumpResult> jump(UUID player, UUID target) {
        return unsupported();
    }

    @Override
    public CompletableFuture<List<UUID>> statusRecipients(UUID player) {
        return unsupported();
    }
}
