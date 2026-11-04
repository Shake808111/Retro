package net.retromc.core.data.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.retromc.core.data.nick.HttpTransport.Response;
import org.junit.jupiter.api.Test;

/** Mojang-, MineSkin-Abrufe und Rate-Limit-Schonung mit HTTP-Attrappe (kein Netz). */
class NickHttpTest {

    private static final UUID SOURCE = UUID.fromString("0f1e2d3c-4b5a-4968-8776-5a4b3c2d1e0f");

    /** Uhr, die beim Schlafen weiterläuft. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        final List<Duration> sleeps = new ArrayList<>();

        void sleep(Duration duration) {
            sleeps.add(duration);
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();

    private RequestPacer pacer(long millis) {
        return new RequestPacer(Duration.ofMillis(millis), clock, clock::sleep);
    }

    @Test
    void namensApiFreiVergebenUndLimit() throws InterruptedException {
        FakeHttp http = new FakeHttp(uri -> switch (uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1)) {
            case "Vergeben" -> Response.of(200, "{\"id\":\"abc\",\"name\":\"Vergeben\"}");
            case "Frei" -> Response.of(404, "");
            case "AltFrei" -> Response.of(204, "");
            case "Limit" -> FakeHttp.tooMany(30);
            case "Netz" -> FakeHttp.IO_ERROR;
            default -> Response.of(500, "");
        });
        RequestPacer pacer = pacer(1100);
        MojangApi api = new MojangApi(http, pacer, "Test/1");
        assertEquals(MojangApi.NameStatus.TAKEN, api.nameStatus("Vergeben"));
        assertEquals(MojangApi.NameStatus.FREE, api.nameStatus("Frei"));
        assertEquals(MojangApi.NameStatus.FREE, api.nameStatus("AltFrei"));
        assertEquals(MojangApi.NameStatus.UNKNOWN, api.nameStatus("Netz"));
        assertEquals(MojangApi.NameStatus.UNKNOWN, api.nameStatus("Kaputt"));
        assertEquals("https://api.minecraftservices.com/minecraft/profile/lookup/name/Vergeben",
                http.calls.getFirst().toString());
        assertEquals("Test/1", http.headers.getFirst().get("User-Agent"));
        // Abstand 1100 ms zwischen den Anfragen (die erste wartet nicht).
        assertEquals(List.of(Duration.ofMillis(1100), Duration.ofMillis(1100), Duration.ofMillis(1100),
                Duration.ofMillis(1100)), clock.sleeps);
        clock.sleeps.clear();
        assertEquals(MojangApi.NameStatus.RATE_LIMITED, api.nameStatus("Limit"));
        assertTrue(pacer.coolingDown());
        api.nameStatus("Frei");
        assertEquals(Duration.ofSeconds(30), clock.sleeps.getLast(), "Retry-After wird eingehalten");
        assertFalse(pacer.coolingDown());
    }

    @Test
    void sessionserverLiefertSignierteTextur() throws InterruptedException {
        String id = SOURCE.toString().replace("-", "");
        FakeHttp http = new FakeHttp(uri -> uri.getPath().endsWith(id)
                ? Response.of(200, FakeHttp.profile(id, "dGV4dHVyZQ==", "c2lnbmF0dXI="))
                : Response.of(204, ""));
        MojangApi api = new MojangApi(http, pacer(0), "Test/1");
        MojangApi.TextureResult result = api.texture(SOURCE);
        assertEquals(MojangApi.FetchStatus.OK, result.status());
        assertEquals(new SkinTexture(id, "dGV4dHVyZQ==", "c2lnbmF0dXI="), result.texture().orElseThrow());
        assertEquals("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false",
                http.calls.getFirst().toString());
        assertEquals(MojangApi.FetchStatus.NOT_FOUND, api.texture(UUID.randomUUID()).status());
        // Ohne Signatur (unsigned) taugt die Textur nicht für den Nick.
        assertTrue(MojangApi.parseTextures(id, "{\"properties\":[{\"name\":\"textures\",\"value\":\"x\"}]}")
                .isEmpty());
        assertTrue(MojangApi.parseTextures(id, "kein json").isEmpty());
    }

    @Test
    void mineSkinMitSchluesselIdsUndListe() throws InterruptedException {
        String detail = "{\"skin\":{\"uuid\":\"%s\",\"texture\":{\"data\":{\"value\":\"v-%s\",\"signature\":"
                + "\"s-%s\"}}}}";
        FakeHttp http = new FakeHttp(uri -> {
            String path = uri.getPath();
            if (uri.getQuery() != null && uri.getQuery().startsWith("size=")) {
                return Response.of(200, "{\"skins\":[{\"uuid\":\"liste1\",\"texture\":{\"data\":{\"value\":\"lv\","
                        + "\"signature\":\"ls\"}}},{\"uuid\":\"liste2\"},{\"uuid\":\"bekannt\"}]}");
            }
            String id = path.substring(path.lastIndexOf('/') + 1);
            return id.equals("weg") ? Response.of(404, "") : Response.of(200, String.format(detail, id, id, id));
        });
        MineSkinApi api = new MineSkinApi(http, pacer(500), "geheim", "Test/1");
        List<SkinTexture> byId = api.skins(List.of("a1", "weg", "bekannt", "b2"), Set.of("bekannt"), 10);
        assertEquals(List.of(new SkinTexture("a1", "v-a1", "s-a1"), new SkinTexture("b2", "v-b2", "s-b2")), byId);
        assertEquals("Bearer geheim", http.headers.getFirst().get("Authorization"));
        assertEquals(0, http.count("bekannt"), "bekannte Skins werden nicht neu geholt");
        http.calls.clear();
        List<SkinTexture> listed = api.skins(List.of(), Set.of("bekannt"), 5);
        assertEquals(List.of(new SkinTexture("liste1", "lv", "ls"), new SkinTexture("liste2", "v-liste2",
                "s-liste2")), listed);
        assertEquals(Optional.empty(), MineSkinApi.texture("x", new com.google.gson.JsonObject()));
        assertFalse(new NickPoolSettings(List.of(), true, Duration.ZERO, "geheim", List.of(), 1, Set.of())
                .toString().contains("geheim"), "Schlüssel nie im Log");
    }
}
