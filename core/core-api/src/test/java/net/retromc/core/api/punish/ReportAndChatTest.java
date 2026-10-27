package net.retromc.core.api.punish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportAndChatTest {

    @Test
    void reportGruendeSpec01A21() {
        assertTrue(ReportReason.RANDOMKILLING.allowedOn("ttt"));
        assertTrue(ReportReason.RANDOMKILLING.allowedOn("REPLAY"));
        assertFalse(ReportReason.RANDOMKILLING.allowedOn("bedwars"));
        assertTrue(ReportReason.HACKING.allowedOn("bedwars"));
        assertEquals(List.of(ReportReason.HACKING, ReportReason.CHAT, ReportReason.TEAMING, ReportReason.NAME,
                ReportReason.BUGUSING), ReportReason.allowedFor("bedwars"));
        assertEquals(6, ReportReason.allowedFor("ttt").size());
        assertEquals(Optional.of(ReportReason.CHAT), ReportReason.byKey("Chat"));
        assertTrue(ReportReason.CHAT.needsChatlog());
        assertFalse(ReportReason.HACKING.needsChatlog());
        assertEquals(Duration.ofSeconds(300), ReportService.TARGET_COOLDOWN);
        assertEquals(10, ReportService.MAX_PER_HOUR);
        assertTrue(ReportStatus.REJECTED.closed());
        assertFalse(ReportStatus.CLAIMED.closed());
        assertThrows(IllegalArgumentException.class, () -> new ReportResult(ReportResult.Outcome.CREATED, null));
    }

    @Test
    void spamWerteSpec01Abschnitt616() {
        assertEquals(Duration.ofMillis(1500), SpamRules.minInterval(0));
        assertEquals(Duration.ofMillis(750), SpamRules.minInterval(300));
        assertTrue(SpamRules.AD_DOMAIN.matcher("komm auf server.net jetzt").find());
        assertTrue(SpamRules.AD_IPV4.matcher("ip 203.0.113.9").find());
        assertFalse(SpamRules.AD_DOMAIN.matcher("gg wp").find());
        assertTrue(SpamRules.REPEAT_EXEMPT.contains("gg"));
        assertTrue(ChatVerdict.CAPS_FIXED.allowed());
        assertFalse(ChatVerdict.REPEAT.allowed());
        assertEquals(Duration.ZERO, ChatCheck.ok("hi").retryAfter());
    }
}
