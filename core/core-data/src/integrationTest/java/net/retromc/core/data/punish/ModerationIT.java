package net.retromc.core.data.punish;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.retromc.core.api.event.Kick;
import net.retromc.core.api.event.MuteUpdate;
import net.retromc.core.api.event.PunishIssued;
import net.retromc.core.api.event.PunishRevoked;
import net.retromc.core.api.event.ReportClosed;
import net.retromc.core.api.event.ReportNew;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatLine;
import net.retromc.core.api.punish.ChatVerdict;
import net.retromc.core.api.punish.Chatlog;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentDuration;
import net.retromc.core.api.punish.PunishmentId;
import net.retromc.core.api.punish.PunishmentOrigin;
import net.retromc.core.api.punish.PunishmentReason;
import net.retromc.core.api.punish.PunishmentRequest;
import net.retromc.core.api.punish.PunishmentStatus;
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.punish.Report;
import net.retromc.core.api.punish.ReportReason;
import net.retromc.core.api.punish.ReportRequest;
import net.retromc.core.api.punish.ReportResult;
import net.retromc.core.api.punish.ReportStatus;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.server.MaintenanceState;
import net.retromc.core.api.util.IpHmac;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.data.server.MaintenanceRepository;
import net.retromc.core.data.server.NetworkSettingRepository;
import net.retromc.core.data.social.DataFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Strafen, Reports, Chatlog, Chat-Prüfung und Wartung gegen PostgreSQL und Valkey (Spec 01 §6.3, §6.9, §6.10,
 * §6.15, §6.16; Abnahme A9, A10, A20, A21, A22).
 */
@Tag("integration")
class ModerationIT {

    private static final byte[] SECRET = "test-secret".getBytes(StandardCharsets.UTF_8);

    private static DataFixture data;
    private static PunishmentRepository punishments;
    private static ReportRepository reports;

    @BeforeAll
    static void start() {
        data = new DataFixture();
        punishments = new PunishmentRepository(data.async, data.valkey, data.events, data.clock);
        reports = new ReportRepository(data.async, data.valkey, () -> data.ranks, data.events, data.clock);
    }

    @AfterAll
    static void stop() {
        data.close();
    }

    private static IpHmac ip(String address) throws UnknownHostException {
        return IpHmac.of(SECRET, InetAddress.getByName(address));
    }

    @Test
    void bannSperrtLoginBisAblaufOderAufhebungUndIpBannGreiftFuerZweitaccount() throws UnknownHostException {
        UUID cheater = data.player("Cheater1");
        UUID alt = data.player("AltAcc1");
        UUID staff = data.player("Staff1");
        IpHmac shared = ip("203.0.113.7");
        assertTrue(await(punishments.activeBan(cheater, shared)).isEmpty());
        // Negativ-Cache 60 s: vor dem Bann gesetzt, wird beim Bann gelöscht
        assertEquals(1L, await(data.valkey.exists(ValkeyKeys.banNone(cheater)).toCompletableFuture()));

        Punishment ban = await(punishments.issue(new PunishmentRequest(cheater, "hacking", null, staff,
                PunishmentOrigin.MANUAL, null, null, "lobby-1", null, shared)));
        assertTrue(PunishmentId.BAN.matcher(ban.publicId()).matches());
        assertEquals(1, ban.stage());
        assertEquals(Duration.ofDays(30), ban.duration().duration());
        assertEquals(ban.publicId(), await(punishments.activeBan(cheater, null)).orElseThrow().publicId());
        assertEquals(ban.publicId(), await(punishments.activeBan(alt, shared)).orElseThrow().publicId());
        assertTrue(await(punishments.activeBan(alt, ip("198.51.100.1"))).isEmpty());
        assertTrue(data.events.of(PunishIssued.class).stream().anyMatch(e -> e.id().equals(ban.publicId())));

        // abgelaufen lässt durch
        data.clock.advance(Duration.ofDays(31));
        await(data.valkey.del(ValkeyKeys.banNone(cheater)).toCompletableFuture());
        assertTrue(await(punishments.activeBan(cheater, null)).isEmpty());
        data.clock.set(Instant.parse("2026-11-02T10:00:00Z"));
        await(data.valkey.del(ValkeyKeys.banNone(cheater)).toCompletableFuture());

        // aufgehoben lässt durch
        assertEquals(ban.publicId(), await(punishments.revoke(ban.publicId(), staff, "Fehler")).orElseThrow()
                .publicId());
        assertTrue(await(punishments.activeBan(cheater, null)).isEmpty());
        assertTrue(data.events.of(PunishRevoked.class).stream().anyMatch(e -> e.id().equals(ban.publicId())));
        assertEquals(PunishmentStatus.REVOKED, await(punishments.byPublicId(ban.publicId())).orElseThrow()
                .status(data.clock.instant()));
    }

    @Test
    void stufenSteigenNurMitNichtAufgehobenenStrafen() {
        UUID player = data.player("Repeat2");
        UUID staff = data.player("Staff2");
        Punishment first = await(punishments.issue(PunishmentRequest.manual(player, "teaming", staff, null, null)));
        Punishment second = await(punishments.issue(PunishmentRequest.manual(player, "teaming", staff, null, null)));
        assertEquals(1, first.stage());
        assertEquals(2, second.stage());
        assertEquals(Duration.ofDays(7), second.duration().duration());
        await(punishments.revoke(second.publicId(), staff, "zu hart"));
        Punishment again = await(punishments.issue(PunishmentRequest.manual(player, "teaming", staff, null, null)));
        assertEquals(2, again.stage());
        Punishment third = await(punishments.issue(PunishmentRequest.manual(player, "teaming", staff, null, null)));
        Punishment capped = await(punishments.issue(PunishmentRequest.manual(player, "teaming", staff, null, null)));
        assertEquals(3, third.stage());
        assertEquals(3, capped.stage());
        Punishment perm = await(punishments.issue(PunishmentRequest.manual(player, "bugusing", staff,
                PunishmentDuration.PERMANENT, null)));
        assertTrue(perm.isPermanent());
        List<Punishment> history = await(punishments.history(player));
        assertEquals(6, history.size());
        assertEquals(perm.publicId(), history.getFirst().publicId());
    }

    @Test
    void muteUeberCacheKickUndWarnUndChatPruefung() {
        UUID player = data.player("Muted3");
        UUID staff = data.player("Staff3");
        ValkeyChatGuard guard = new ValkeyChatGuard(data.valkey, () -> punishments, new SpamFilter(Set.of()),
                data.clock);
        ChatContext chat = new ChatContext(ChatContext.Source.CHAT, 0, false);
        assertEquals(ChatVerdict.OK, await(guard.check(player, "hallo", chat)).verdict());
        // Negativ-Eintrag im Cache
        assertEquals("1", await(data.valkey.hget(ValkeyKeys.mute(player), "none").toCompletableFuture()));

        Punishment mute = await(punishments.issue(PunishmentRequest.manual(player, "chat_spam", staff, null, null)));
        assertTrue(PunishmentId.MUTE.matcher(mute.publicId()).matches());
        Map<String, String> cached = await(data.valkey.hgetall(ValkeyKeys.mute(player)).toCompletableFuture());
        assertEquals(mute.publicId(), cached.get("publicId"));
        assertEquals(mute.publicId(), await(punishments.activeMute(player)).orElseThrow().publicId());
        assertTrue(data.events.of(MuteUpdate.class).stream().anyMatch(e -> e.id().equals(mute.publicId())));
        // A10: Mute blockiert auch PN, Party- und Clan-Chat (Proxy) sowie das Team
        for (ChatContext.Source source : ChatContext.Source.values()) {
            ChatCheck check = await(guard.check(player, "text", new ChatContext(source, 900, true)));
            assertEquals(ChatVerdict.MUTED, check.verdict(), source.name());
        }
        await(punishments.revokeActive(player, PunishmentType.MUTE, staff, "ok"));
        assertTrue(await(punishments.activeMute(player)).isEmpty());
        data.clock.advance(Duration.ofSeconds(5));
        assertEquals(ChatVerdict.OK, await(guard.check(player, "wieder da", chat)).verdict());

        Punishment kick = await(punishments.kick(player, "Benehmen", staff, "lobby-1"));
        assertEquals(PunishmentType.KICK, kick.type());
        assertEquals(PunishmentStatus.EXPIRED, kick.status(data.clock.instant()));
        assertTrue(data.events.of(Kick.class).stream().anyMatch(e -> e.uuid().equals(player)));
        Punishment warn = await(punishments.warn(player, "Letzte Warnung", staff, null));
        assertEquals(PunishmentType.WARN, warn.type());
        assertEquals("Letzte Warnung", warn.reasonText());
        assertEquals(3, await(punishments.history(player)).size());
        data.clock.set(Instant.parse("2026-11-02T10:00:00Z"));
    }

    @Test
    void gruendeAusDemSeed() {
        List<PunishmentReason> reasons = await(punishments.reasons());
        assertEquals(11, reasons.size());
        PunishmentReason randomkilling = await(punishments.reason("randomkilling")).orElseThrow();
        assertTrue(randomkilling.reportableOn("TTT"));
        assertFalse(randomkilling.reportableOn("bedwars"));
        assertEquals(PunishmentType.MUTE, await(punishments.reason("chat_ad")).orElseThrow().type());
        assertTrue(await(punishments.reason("gibtsnicht")).isEmpty());
    }

    @Test
    void reportSperrenChatlogUndAbschluss() {
        UUID reporter = data.player("Reporter4");
        UUID target = data.player("Target4");
        UUID staff = data.player("Staff4");
        data.ranks.set(staff, Rank.MOD);
        assertEquals(ReportResult.Outcome.SELF, await(reports.create(request(reporter, reporter,
                ReportReason.HACKING, "bedwars"))).outcome());
        assertEquals(ReportResult.Outcome.REASON_NOT_ALLOWED, await(reports.create(request(reporter, target,
                ReportReason.RANDOMKILLING, "bedwars"))).outcome());
        assertEquals(ReportResult.Outcome.STAFF, await(reports.create(request(reporter, staff,
                ReportReason.HACKING, "bedwars"))).outcome());

        await(reports.bufferChat("ttt-1", new ChatLine(target, "Target4", "du bist schlecht",
                data.clock.instant().minusSeconds(30))));
        await(reports.bufferChat("ttt-1", new ChatLine(reporter, "Reporter4", "hör auf", data.clock.instant())));
        ReportResult created = await(reports.create(new ReportRequest(reporter, target, ReportReason.CHAT, "ttt-1",
                "ttt", null)));
        assertEquals(ReportResult.Outcome.CREATED, created.outcome());
        Report report = created.report();
        assertTrue(PunishmentId.REPORT.matcher(report.publicId()).matches());
        Chatlog chatlog = await(reports.chatlog(report.chatlogId())).orElseThrow();
        assertEquals(2, chatlog.messages().size());
        assertEquals("du bist schlecht", chatlog.messages().getFirst().text());
        assertTrue(data.events.of(ReportNew.class).stream().anyMatch(e -> e.id().equals(report.publicId())));

        // A21: gleiches Ziel innerhalb 300 s gesperrt
        assertEquals(ReportResult.Outcome.COOLDOWN, await(reports.create(request(reporter, target,
                ReportReason.HACKING, "ttt"))).outcome());
        // 10 pro Stunde
        for (int i = 1; i < 10; i++) {
            assertEquals(ReportResult.Outcome.CREATED, await(reports.create(request(reporter, data.player("Many" + i),
                    ReportReason.HACKING, "bedwars"))).outcome());
        }
        assertEquals(ReportResult.Outcome.LIMIT, await(reports.create(request(reporter, data.player("Eleven4"),
                ReportReason.HACKING, "bedwars"))).outcome());

        assertEquals(10, await(reports.open()).size());
        Report claimed = await(reports.claim(report.publicId(), staff)).orElseThrow();
        assertEquals(ReportStatus.CLAIMED, claimed.status());
        assertTrue(await(reports.claim(report.publicId(), staff)).isEmpty());
        Report closed = await(reports.close(report.publicId(), staff, true)).orElseThrow();
        assertEquals(ReportStatus.ACCEPTED, closed.status());
        assertTrue(await(reports.close(report.publicId(), staff, false)).isEmpty());
        assertTrue(data.events.of(ReportClosed.class).stream().anyMatch(e -> e.id().equals(report.publicId())
                && e.justified()));
        assertEquals(1, await(reports.resultsSince(reporter, data.clock.instant().minusSeconds(1))).size());
        assertTrue(await(reports.resultsSince(reporter, data.clock.instant())).isEmpty());
    }

    @Test
    void chatlogNurMitZeilenDesZiels() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        await(reports.bufferChat("lobby-9", new ChatLine(a, "A", "alt", data.clock.instant().minusSeconds(1200))));
        assertTrue(await(reports.createChatlog(b, a, "lobby-9")).isEmpty());
        await(reports.bufferChat("lobby-9", new ChatLine(a, "A", "neu", data.clock.instant())));
        Chatlog log = await(reports.createChatlog(b, a, "lobby-9")).orElseThrow();
        assertEquals(List.of("neu"), log.messages().stream().map(ChatLine::text).toList());
        assertNull(ReportRepository.parse("kaputt"));
    }

    @Test
    void wartungStatusWhitelistUndEinstellungen() {
        MaintenanceRepository maintenance = new MaintenanceRepository(data.async, data.valkey, data.events,
                data.clock);
        NetworkSettingRepository settings = new NetworkSettingRepository(data.async);
        await(data.valkey.del(ValkeyKeys.maintenance()).toCompletableFuture());
        assertEquals(MaintenanceState.OFF, await(maintenance.state()));
        MaintenanceState on = await(maintenance.enable(null, "Update"));
        assertTrue(on.enabled());
        assertTrue(await(maintenance.state()).enabled());
        await(data.valkey.del(ValkeyKeys.maintenance()).toCompletableFuture());
        assertEquals("Update", await(maintenance.state()).reason());
        assertTrue(await(settings.get("maintenance")).orElseThrow().contains("true"));
        UUID friend = UUID.randomUUID();
        assertTrue(await(maintenance.addToWhitelist(friend, null)));
        assertFalse(await(maintenance.addToWhitelist(friend, null)));
        assertTrue(await(maintenance.isWhitelisted(friend)));
        assertEquals(List.of(friend), await(maintenance.whitelist()));
        assertTrue(await(maintenance.removeFromWhitelist(friend)));
        assertFalse(await(maintenance.isWhitelisted(friend)));
        assertFalse(await(maintenance.disable(null)).enabled());
        assertFalse(await(maintenance.state()).enabled());
        await(settings.put("motd", "{\"news\":\"DUELS NEUE MAPS\"}"));
        assertTrue(await(settings.all()).containsKey("motd"));
    }

    private static ReportRequest request(UUID reporter, UUID target, ReportReason reason, String mode) {
        return new ReportRequest(reporter, target, reason, mode + "-1", mode, null);
    }
}
