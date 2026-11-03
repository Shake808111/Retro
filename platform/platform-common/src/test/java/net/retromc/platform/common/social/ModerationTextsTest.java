package net.retromc.platform.common.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.event.ClanUpdate;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.punish.Punishment;
import net.retromc.core.api.punish.PunishmentOrigin;
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.text.CatalogI18n;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Bildschirme der Moderation (Spec 01 §7, Abnahme A8) und Weiterleitung der Ereignisse an den Bus. */
class ModerationTextsTest {

    private static final Instant ISSUED = Instant.parse("2026-11-02T10:00:00Z");
    private final ModerationTexts texts = new ModerationTexts(new CatalogI18n(player -> Optional.empty()));

    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private static Punishment punishment(PunishmentType type, String code, Instant expires) {
        return new Punishment(1, "Ab3dE5gH7jK", UUID.randomUUID(), type, code, null, null, ISSUED, expires, null,
                null, null, null, null, PunishmentOrigin.MANUAL, null, 1);
    }

    @Test
    void permanenterBannBildschirm() {
        Component screen = texts.banScreen(Locale.GERMANY, punishment(PunishmentType.BAN, "hacking", null), ISSUED,
                "https://example.net/entbannung");
        assertEquals("§cDu wurdest §4PERMANENT §cvom Netzwerk gebannt.\n"
                + "§3Grund: §cHacking [Ab3dE5gH7jK]\n"
                + "\n"
                + "§aEinen Entbannungsantrag kannst du unter §ehttps://example.net/entbannung §astellen.",
                legacy(screen));
    }

    @Test
    void zeitBannBildschirmMitDauerUndRestzeit() {
        Instant expires = ISSUED.plus(Duration.ofDays(30));
        Instant now = ISSUED.plus(Duration.ofHours(10).plusMinutes(17).plusSeconds(26));
        Component screen = texts.banScreen(Locale.GERMANY, punishment(PunishmentType.BAN, "hacking", expires), now,
                "https://example.net/entbannung");
        assertEquals("§cDu wurdest für §e30d 0h 0m 0s §cvom Netzwerk gebannt!\n"
                + "§3Grund: §cHacking [Ab3dE5gH7jK]\n"
                + "\n"
                + "§3Verbleibende Zeit: §e29d 13h 42m 34s\n"
                + "§aEinen Entbannungsantrag kannst du unter §ehttps://example.net/entbannung §astellen.",
                legacy(screen));
    }

    @Test
    void muteHinweisKickUndHistorie() {
        Punishment mute = punishment(PunishmentType.MUTE, "chat_respect", ISSUED.plus(Duration.ofDays(3)));
        String notice = PlainTextComponentSerializer.plainText().serialize(texts.muteNotice(Locale.GERMANY, mute,
                ISSUED.plus(Duration.ofHours(1))));
        assertTrue(notice.contains("3d 0h 0m 0s"), notice);
        assertTrue(notice.contains("Respektloses und provokantes Verhalten [Ab3dE5gH7jK]"), notice);
        assertTrue(notice.contains("2d 23h 0m 0s"), notice);
        Punishment kick = new Punishment(2, "ABCDEFGH", UUID.randomUUID(), PunishmentType.KICK, "kick", "Benimm dich",
                null, ISSUED, ISSUED, null, null, null, null, null, PunishmentOrigin.MANUAL, null, 1);
        assertEquals("Benimm dich", PlainTextComponentSerializer.plainText().serialize(texts.reason(Locale.GERMANY,
                kick)));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(texts.historyLine(Locale.GERMANY, kick,
                ISSUED)).contains("02.11.2026 11:00"));
        assertEquals("§cDu wurdest vom Netzwerk gekickt.\n§3Grund: §cSpam", legacy(texts.kickScreen(Locale.GERMANY,
                "Spam")));
    }

    @Test
    void ereignisseGehenAufDenPassendenKanal() {
        List<MessageType<?>> sent = new ArrayList<>();
        MessageBus bus = new MessageBus() {
            @Override
            public String source() {
                return "test";
            }

            @Override
            public <P> Envelope<P> envelope(MessageType<P> type, P payload) {
                sent.add(type);
                return new Envelope<>(type.name(), 1, UUID.randomUUID(), 0, "test", null, payload);
            }

            @Override
            public <R> Envelope<R> reply(Envelope<?> request, MessageType<R> type, R payload) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> publish(Envelope<?> envelope) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <P> CompletableFuture<Registration> subscribe(MessageType<P> type,
                    Consumer<Envelope<P>> handler) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
            }
        };
        BusEventSink sink = new BusEventSink(() -> bus, LoggerFactory.getLogger(ModerationTextsTest.class));
        sink.publish(new MaintenanceChanged(true));
        sink.publish(new ClanUpdate(1, "T", List.of()));
        assertEquals(List.of(MessageType.MAINTENANCE_CHANGED, MessageType.CLAN_UPDATE), sent);
        assertThrows(IllegalArgumentException.class, () -> sink.publish(Map.of()));
    }
}
