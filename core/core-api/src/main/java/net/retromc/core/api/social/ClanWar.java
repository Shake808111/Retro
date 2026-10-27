package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.stats.ModeKey;
import org.jspecify.annotations.Nullable;

/**
 * Clan-War {@code social.clan_war} (Spec 01 §2.2; Eigentümer der Elo, Spec 00 §5 Nr. 13).
 *
 * @param winner Sieger-Clan oder {@code null} (Unentschieden)
 * @param eloDelta Elo-Änderung des Siegers bzw. von {@code clanA} bei Unentschieden; 0 bei FunClanWar
 * @param hidden nach {@code /clanstatsreset} ausgeblendet
 */
public record ClanWar(
        long id,
        ModeKey mode,
        boolean ranked,
        long clanA,
        long clanB,
        @Nullable Long winner,
        int eloDelta,
        @Nullable UUID matchId,
        boolean hidden,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt) {

    public ClanWar {
        Objects.requireNonNull(mode, "mode");
        if (clanA == clanB) {
            throw new IllegalArgumentException("Ein Clan kann nicht gegen sich selbst spielen");
        }
        if (winner != null && winner != clanA && winner != clanB) {
            throw new IllegalArgumentException("Sieger muss clanA oder clanB sein");
        }
    }
}
