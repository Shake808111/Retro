package net.retromc.velocity.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.retromc.core.api.punish.ReportResult;
import net.retromc.core.api.server.MaintenanceState;
import net.retromc.core.api.social.ClanOutcome;
import net.retromc.core.api.social.FriendOutcome;
import net.retromc.core.api.social.IgnoreOutcome;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyMember;
import net.retromc.core.api.social.PartyOutcome;
import net.retromc.core.api.social.PublicFilter;
import net.retromc.core.api.util.Ulid;
import net.retromc.core.i18n.Messages;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.velocity.core.clan.ClanFeature;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.friend.FriendFeature;
import net.retromc.velocity.core.joinme.JoinMeFeature;
import net.retromc.velocity.core.maintenance.MaintenanceFeature;
import net.retromc.velocity.core.motd.MotdBuilder;
import net.retromc.velocity.core.motd.MotdFeature;
import net.retromc.velocity.core.motd.MotdSettings;
import net.retromc.velocity.core.msg.MsgFeature;
import net.retromc.velocity.core.nick.NickFeature;
import net.retromc.velocity.core.party.PartyFeature;
import net.retromc.velocity.core.punish.PunishFeature;
import net.retromc.velocity.core.report.ReportFeature;
import org.junit.jupiter.api.Test;

/** Proxy-Teile ohne laufenden Velocity: Befehle (A25), signierter Chat (A10), Wartung/MOTD (A22), Textschlüssel. */
class SocialProxyTest {

    private static List<CommandName> all() {
        List<CommandName> all = new ArrayList<>();
        Stream.of(FriendFeature.COMMANDS, PartyFeature.COMMANDS, MsgFeature.COMMANDS, ClanFeature.COMMANDS,
                PunishFeature.COMMANDS, ReportFeature.COMMANDS, MaintenanceFeature.COMMANDS, MotdFeature.COMMANDS,
                NickFeature.COMMANDS, JoinMeFeature.COMMANDS).forEach(all::addAll);
        return all;
    }

    @Test
    void alleBefehleUndAliaseAusParagraph3SindRegistriert() {
        Set<String> names = new TreeSet<>();
        for (CommandName command : all()) {
            assertTrue(names.add(command.name()), "doppelt: " + command.name());
            command.aliases().forEach(alias -> assertTrue(names.add(alias), "doppelt: " + alias));
        }
        // Spec 01 §3.1–§3.6 (ohne /l /hub /gameteleport /rank /language /settings /tc: andere Pakete)
        Set<String> expected = Set.of("friend", "friends", "fr", "msg", "tell", "whisper", "r", "reply", "ignore",
                "unignore", "party", "pi", "pa", "pl", "pk", "ppm", "pj", "pp", "p", "partytoggle", "clan", "cc",
                "clc", "clanlvl", "clanstatsreset", "joinme", "nick", "unnick", "nicks", "keepnick", "report",
                "chatlog", "ban", "mute", "kick", "warn", "unban", "pardon", "unmute", "history", "reports", "goto",
                "maintenance", "wartung", "netreload");
        assertEquals(new TreeSet<>(expected), names);
    }

    @Test
    void velocityBrichtSigniertenChatNieAb() throws IOException {
        Path sources = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file);
                assertFalse(code.contains("PlayerChatEvent"), file + " fasst PlayerChatEvent an");
                assertFalse(code.contains("ChatResult.denied"), file + " verwirft Chat");
            }
        }
    }

    @Test
    void wartungLaesstNurWhitelistUndTeamDurch() {
        MaintenanceState on = new MaintenanceState(true, null, Instant.EPOCH);
        assertFalse(MaintenanceFeature.allowed(on, false, false));
        assertTrue(MaintenanceFeature.allowed(on, true, false));
        assertTrue(MaintenanceFeature.allowed(on, false, true));
        assertTrue(MaintenanceFeature.allowed(MaintenanceState.OFF, false, false));
    }

    @Test
    void motdZeigtWartungUndNews() {
        MotdBuilder motd = new MotdBuilder(new CatalogI18n(player -> Optional.empty()));
        Locale de = Messages.DEFAULT_LOCALE;
        MotdSettings settings = MotdSettings.parse("{\"news\":\"duels neue maps\",\"maxPlayers\":20000}");
        assertEquals(20000, settings.maxPlayers());
        assertEquals("§9DUELS §dNEUE MAPS", LegacyComponentSerializer.legacySection().serialize(
                motd.line2(de, settings, false)));
        assertEquals("§c§lWARTUNGSARBEITEN", LegacyComponentSerializer.legacySection().serialize(
                motd.line2(de, settings, true)));
        MotdBuilder.Version version = motd.version(de, settings, true, 774, 774, "Velocity");
        assertEquals(-1, version.protocol());
        assertEquals("§cWartung", version.name());
        assertEquals("1.21.11+", motd.version(de, settings, false, 770, 774, "Velocity").name());
        assertEquals("Velocity", motd.version(de, settings, false, 774, 774, "Velocity").name());
        String line1 = LegacyComponentSerializer.legacySection().serialize(motd.line1(de));
        assertTrue(line1.startsWith(" "), line1);
        assertEquals(MotdSettings.DEFAULT, MotdSettings.parse("kaputt"));
    }

    @Test
    void jedesErgebnisHatEinenText() {
        CatalogI18n i18n = new CatalogI18n(player -> Optional.empty());
        for (FriendOutcome outcome : FriendOutcome.values()) {
            if (!Set.of(FriendOutcome.SENT, FriendOutcome.ADDED, FriendOutcome.DENIED, FriendOutcome.REMOVED)
                    .contains(outcome)) {
                assertTrue(i18n.has(FriendFeature.errorKey(outcome)), outcome.name());
            }
        }
        for (PartyOutcome outcome : PartyOutcome.values()) {
            if (!Set.of(PartyOutcome.INVITED, PartyOutcome.JOINED, PartyOutcome.REQUEST_SENT, PartyOutcome.DENIED,
                    PartyOutcome.LEFT, PartyOutcome.KICKED, PartyOutcome.PROMOTED, PartyOutcome.DISBANDED,
                    PartyOutcome.UPDATED).contains(outcome)) {
                assertTrue(i18n.has(PartyFeature.errorKey(outcome)), outcome.name());
            }
        }
        for (ClanOutcome outcome : ClanOutcome.values()) {
            try {
                assertTrue(i18n.has(ClanFeature.errorKey(outcome)), outcome.name());
            } catch (IllegalArgumentException success) {
                // Erfolg, kein Fehlertext
            }
        }
        for (ReportResult.Outcome outcome : ReportResult.Outcome.values()) {
            assertTrue(i18n.has(ReportFeature.resultKey(outcome)), outcome.name());
        }
        for (IgnoreOutcome outcome : IgnoreOutcome.values()) {
            assertTrue(i18n.has(MsgFeature.ignoreKey(outcome)), outcome.name());
        }
    }

    @Test
    void zeitenUndAfkEmpfaenger() {
        assertEquals(Duration.ofHours(3), ClanFeature.untilNextRun(Instant.parse("2026-11-02T00:00:00Z")));
        assertEquals(Duration.ofHours(23), ClanFeature.untilNextRun(Instant.parse("2026-11-02T04:00:00Z")));
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Party party = new Party(Ulid.next(), leader, Instant.EPOCH, false,
                PublicFilter.ALL, false, 3, List.of(new PartyMember(leader, Instant.EPOCH),
                        new PartyMember(member, Instant.EPOCH.plusSeconds(1))));
        // A16: AFK-Meldung an die Party, nicht an den AFK-Spieler selbst
        assertEquals(List.of(leader), PartyFeature.afkRecipients(party, member));
    }
}
