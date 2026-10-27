package net.retromc.core.api.social;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.LevelCurve;
import org.jspecify.annotations.Nullable;

/**
 * Clans (Spec 01 §3.4, §5.4, §5.5, §6.6, §8.1). Genutzt von Velocity (Befehle), paper-core (GUIs, Scoreboard),
 * {@code game:clanwar} ({@link #recordWar}) und Stats. Ereignisse {@code CLAN_UPDATE} und
 * {@code PROFILE_INVALIDATE} (clanTag/clanId) sendet die Implementierung.
 */
public interface ClanService {

    CompletableFuture<Optional<Clan>> clanOf(UUID player);

    CompletableFuture<Optional<Clan>> clan(long clanId);

    /** Suche ohne Groß-/Kleinschreibung ({@code /clan join <Clanname>}, {@code /clan cwstats <Clanname>}). */
    CompletableFuture<Optional<Clan>> byName(String name);

    /** Suche ohne Groß-/Kleinschreibung ({@code /clan request <Tag>}, {@code /clan info [Tag]}). */
    CompletableFuture<Optional<Clan>> byTag(String tag);

    /** Mitglieder; Sortierung über {@link ClanMember#ROLE_ORDER}. */
    CompletableFuture<List<ClanMember>> members(long clanId);

    /** Rolle des Spielers in seinem Clan. */
    CompletableFuture<Optional<ClanRole>> role(UUID player);

    /** Online/gesamt (online = Präsenz ohne {@code hidden}; Scoreboard „Clan“). */
    CompletableFuture<OnlineCount> counts(long clanId);

    /** Clan-Tag des Spielers (Chat, Tab, Nametag). */
    CompletableFuture<Optional<String>> tag(UUID player);

    /** {@code 30 + Bonus des Leiter-Rangs} ({@link ClanRules#maxMembers}). */
    CompletableFuture<Integer> maxMembers(long clanId);

    /** Gründen; prüft {@code ClanName}/{@code ClanTag} und Eindeutigkeit ohne Groß-/Kleinschreibung. */
    CompletableFuture<ClanResult> create(UUID owner, String name, String tag);

    /** Einladen (Moderator+, 7 Tage gültig). */
    CompletableFuture<ClanResult> invite(UUID actor, UUID target);

    /** Offene Einladungen an den Spieler. */
    CompletableFuture<List<ClanInvite>> invites(UUID player);

    /** Einladung annehmen ({@code /clan join <Clanname>}). */
    CompletableFuture<ClanResult> join(UUID player, String clanName);

    /** Beitrittsanfrage, wenn {@code join_mode = 1}. */
    CompletableFuture<ClanResult> request(UUID player, String tag);

    /** Offene Anfragen an den Clan, älteste zuerst. */
    CompletableFuture<List<ClanJoinRequest>> requests(long clanId);

    /** Anfrage annehmen (Moderator+); der Spieler wird Testmitglied. */
    CompletableFuture<ClanResult> acceptRequest(UUID actor, UUID requester);

    CompletableFuture<ClanResult> denyRequest(UUID actor, UUID requester);

    /** Verlassen; der Leiter erhält {@link ClanOutcome#LEADER_CANNOT_LEAVE}. */
    CompletableFuture<ClanResult> leave(UUID player);

    /** Rauswerfen (Moderator+, nur niedrigere Rolle). */
    CompletableFuture<ClanResult> kick(UUID actor, UUID target);

    /** Eine Stufe hoch; Moderator → Leiter liefert {@link ClanOutcome#CONFIRM_TRANSFER}. */
    CompletableFuture<ClanResult> promote(UUID leader, UUID target);

    /** Leitung nach Bestätigung übertragen; der bisherige Leiter wird Moderator. */
    CompletableFuture<ClanResult> transferLeadership(UUID leader, UUID target);

    CompletableFuture<ClanResult> demote(UUID leader, UUID target);

    /** Name und Tag ändern (Leiter, höchstens 2×/Tag Europe/Berlin, Log {@code clan_rename_log}). */
    CompletableFuture<ClanResult> rename(UUID leader, String name, String tag);

    /** Löschen nach Bestätigung ({@code /clan delete confirm}). */
    CompletableFuture<ClanResult> delete(UUID leader);

    /** Beitrittsmodus (GUI §5.5). */
    CompletableFuture<ClanResult> setJoinMode(UUID leader, ClanJoinMode mode);

    /** Ränge hervorheben (GUI §5.5, Leiter). */
    CompletableFuture<ClanResult> setShowRoles(UUID leader, boolean showRoles);

    /** Freigeschaltete Statistik-Modi ({@code social.clan_stats_mode}). */
    CompletableFuture<Set<ModeKey>> statsModes(long clanId);

    /** Modus freischalten/sperren ({@code /clan togglestats}); liefert STATS_ENABLED oder STATS_DISABLED. */
    CompletableFuture<ClanResult> toggleStatsMode(UUID leader, ModeKey mode);

    /** {@code /clan stats [Modus]}; leer, wenn der Modus nicht freigeschaltet ist. */
    CompletableFuture<Optional<ClanModeStats>> stats(long clanId, ModeKey mode);

    /** {@code /clan cwstats}; leer, wenn der Clan in dem Modus noch keine Elo hat. */
    CompletableFuture<Optional<ClanWarStats>> warStats(long clanId, ModeKey mode);

    /**
     * Clan-War verbuchen ({@code game:clanwar}): {@code social.clan_war}, bei {@code ranked} Elo nach
     * {@code EloCalculator} (K=50, Nullsumme) in {@code social.clan_elo}, XP nach {@link ClanRules#warXp}.
     *
     * @param winner Sieger-Clan oder {@code null} bei Unentschieden
     */
    CompletableFuture<ClanWar> recordWar(ModeKey mode, long clanA, long clanB, @Nullable Long winner, boolean ranked,
            @Nullable UUID matchId);

    /** Elo im Modus; ohne Eintrag 1000. */
    CompletableFuture<Integer> elo(long clanId, ModeKey mode);

    /** Heute gespielte Clan-Wars (Limit {@link ClanRules#WARS_PER_DAY}). */
    CompletableFuture<Integer> warsToday(long clanId, ModeKey mode);

    /** XP gutschreiben; liefert den neuen Level-Stand. */
    CompletableFuture<LevelCurve.Progress> addXp(long clanId, long xp);

    /** {@code /clanlvl}. */
    CompletableFuture<LevelCurve.Progress> level(long clanId);

    /**
     * {@code /clanstatsreset <Modus>} nach Bestätigung: 1 ResetToken des Leiters, Elo 1000, Spiele/Siege/Niederlagen
     * 0, Clan-Wars {@code hidden=true} – alles in einer Transaktion.
     */
    CompletableFuture<ClanResult> resetStats(UUID leader, ModeKey mode);

    /** Login eines Mitglieds: {@code last_active = now()} (Spec 01 §6.3 Schritt 6). */
    CompletableFuture<Void> markActive(UUID player);

    /** Inaktivitäts-Job: Clans mit {@code last_active < cutoff} löschen; liefert die Anzahl. */
    CompletableFuture<Integer> deleteInactive(Instant cutoff);

    /**
     * Empfänger von {@code CLAN_ONLINE/OFFLINE}: Mitglieder mit {@code notify_clan}; leer bei {@code hide_from_clan}.
     */
    CompletableFuture<List<UUID>> statusRecipients(UUID player);
}
