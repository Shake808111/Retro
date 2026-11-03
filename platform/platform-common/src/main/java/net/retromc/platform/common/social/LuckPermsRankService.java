package net.retromc.platform.common.social;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link RankService} über LuckPerms (Spec 01 §1, §6.1): primäre Gruppe → {@link Rank}, Ablauf eines
 * Zeitrangs aus dem Vererbungs-Knoten. Ein Adapter an ein bestehendes Kern-Plugin ersetzt ihn über die
 * Service-Registry.
 */
public final class LuckPermsRankService implements RankService {

    private final LuckPerms luckPerms;

    private LuckPermsRankService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** LuckPerms, wenn installiert und geladen. */
    public static Optional<RankService> create() {
        try {
            return Optional.of(new LuckPermsRankService(LuckPermsProvider.get()));
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<RankInfo> rank(UUID player) {
        User loaded = luckPerms.getUserManager().getUser(player);
        CompletableFuture<User> user = loaded != null
                ? CompletableFuture.completedFuture(loaded) : luckPerms.getUserManager().loadUser(player);
        return user.thenApply(LuckPermsRankService::info);
    }

    @Override
    public CompletableFuture<Map<UUID, RankInfo>> ranks(Collection<UUID> players) {
        List<UUID> distinct = players.stream().distinct().toList();
        List<CompletableFuture<RankInfo>> lookups = distinct.stream().map(this::rank).toList();
        return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> distinct.stream().collect(Collectors.toMap(Function.identity(),
                        player -> lookups.get(distinct.indexOf(player)).join())));
    }

    static RankInfo info(@Nullable User user) {
        if (user == null) {
            return RankInfo.of(Rank.DEFAULT);
        }
        String group = user.getPrimaryGroup();
        Rank rank = Rank.byGroup(group).orElse(Rank.DEFAULT);
        Instant expires = user.getNodes(NodeType.INHERITANCE).stream()
                .filter(node -> node.getGroupName().equalsIgnoreCase(group) && node.hasExpiry())
                .map(InheritanceNode::getExpiry).filter(Objects::nonNull).findFirst().orElse(null);
        return withExpiry(RankInfo.of(rank), expires);
    }

    static RankInfo withExpiry(RankInfo base, @Nullable Instant expires) {
        return new RankInfo(base.id(), base.displayName(), base.prefix(), base.legacyColor(), base.gradient(),
                base.weight(), base.limits(), base.flags(), expires);
    }

    /** Rückfall ohne LuckPerms: alle Spieler haben den Standardrang. */
    public static RankService everyoneDefault() {
        return new RankService() {
            @Override
            public CompletableFuture<RankInfo> rank(UUID player) {
                return CompletableFuture.completedFuture(RankInfo.of(Rank.DEFAULT));
            }

            @Override
            public CompletableFuture<Map<UUID, RankInfo>> ranks(Collection<UUID> players) {
                return CompletableFuture.completedFuture(players.stream().distinct()
                        .collect(Collectors.toMap(Function.identity(), player -> RankInfo.of(Rank.DEFAULT))));
            }
        };
    }
}
