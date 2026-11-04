package net.retromc.core.data.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.retromc.core.api.social.NickPoolService.Fill;
import net.retromc.core.api.social.NickPoolService.FillStatus;
import net.retromc.core.data.nick.HttpTransport.Response;
import net.retromc.core.data.punish.SpamFilter;
import org.junit.jupiter.api.Test;

/** Füllvorgang mit Speicher- und HTTP-Attrappe (Spec 01 §6.11, keine echten Netzaufrufe). */
class NickPoolFillerTest {

    private static final UUID A = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID B = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    /** Speicher im Arbeitsspeicher. */
    static final class MemoryStore implements NickPoolFiller.Store {
        final Map<String, Set<String>> refs = new HashMap<>();
        final List<NickPoolFiller.StoredSkin> skins = new ArrayList<>();
        final Map<String, NickPoolFiller.StoredSkin> names = new LinkedHashMap<>();
        final Set<String> players = new HashSet<>();

        @Override
        public Set<String> skinRefs(String source) {
            return Set.copyOf(refs.getOrDefault(source, Set.of()));
        }

        @Override
        public void addSkins(String source, List<SkinTexture> added) {
            for (SkinTexture skin : added) {
                if (refs.computeIfAbsent(source, key -> new HashSet<>()).add(skin.sourceRef())) {
                    skins.add(new NickPoolFiller.StoredSkin(skins.size() + 1, skin.value(), skin.signature()));
                }
            }
        }

        @Override
        public List<NickPoolFiller.StoredSkin> enabledSkins() {
            return List.copyOf(skins);
        }

        @Override
        public Set<String> knownNames(Collection<String> lowerNames) {
            Set<String> known = new HashSet<>();
            for (String name : lowerNames) {
                if (players.contains(name) || names.containsKey(name)) {
                    known.add(name);
                }
            }
            return known;
        }

        @Override
        public boolean addName(String name, NickPoolFiller.StoredSkin skin) {
            return names.putIfAbsent(name.toLowerCase(Locale.ROOT), skin) == null;
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final NickHttpTest.ManualClock clock = new NickHttpTest.ManualClock();

    private NickPoolFiller filler(FakeHttp http, NickPoolSettings settings, long seed) {
        MojangApi mojang = new MojangApi(http, new RequestPacer(settings.mojangInterval(), clock, clock::sleep),
                "Test/1");
        MineSkinApi mineSkin = settings.mineSkinKey() == null ? null : new MineSkinApi(http,
                new RequestPacer(Duration.ZERO, clock, clock::sleep), settings.mineSkinKey(), "Test/1");
        return new NickPoolFiller(store, new NickNameGenerator(new SplittableRandom(seed)),
                new NickNameFilter(new SpamFilter(Set.of()), settings.blockedWords()),
                name -> name.equalsIgnoreCase("OnlineOtto"), mojang, mineSkin, settings, new SplittableRandom(seed));
    }

    private static NickPoolSettings settings(boolean checkMojang, List<UUID> sources) {
        return new NickPoolSettings(sources, checkMojang, Duration.ofMillis(1100), null, List.of(), 5, Set.of());
    }

    /** Sessionserver liefert für jede UUID eine Textur, die Namens-API meldet „frei“ außer für {@code taken}. */
    private static FakeHttp mojang(Set<String> taken) {
        return new FakeHttp(uri -> {
            String last = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
            if (uri.getHost().startsWith("sessionserver")) {
                return Response.of(200, FakeHttp.profile(last, "wert-" + last, "sig-" + last));
            }
            return taken.contains(last) ? Response.of(200, "{}") : Response.of(404, "");
        });
    }

    @Test
    void fuelltMitSkinsAusBeidenQuellUuidsUndPrueftJedenNamen() throws InterruptedException {
        FakeHttp http = mojang(Set.of());
        Fill fill = filler(http, settings(true, List.of(A, B)), 1).fill(25);
        assertEquals(FillStatus.DONE, fill.status());
        assertEquals(25, fill.added());
        assertEquals(2, fill.skinsAdded());
        assertEquals(2, fill.skins());
        assertEquals(25, store.names.size());
        assertEquals(2, http.count("sessionserver"));
        assertEquals(2, http.count("unsigned=false"));
        assertEquals(25, http.count("lookup/name/"), "jeder neue Name einmal bei Mojang geprüft");
        // Zufällige Kombination: beide Skins kommen vor.
        assertEquals(2, new HashSet<>(store.names.values()).size());
        // Pacing: jede Mojang-Anfrage nach der ersten wartet 1,1 s.
        assertEquals(2 + 25 - 1, clock.sleeps.size());

        // Zweiter Lauf holt vorhandene Skins nicht noch einmal.
        http.calls.clear();
        Fill second = filler(http, settings(true, List.of(A, B)), 2).fill(5);
        assertEquals(0, second.skinsAdded());
        assertEquals(0, http.count("sessionserver"));
        assertEquals(30, store.names.size());
    }

    @Test
    void verwirftBekannteGefilterteUndVergebeneNamen() throws InterruptedException {
        // Erst die Namen eines Laufs kennen, dann denselben Zufall mit Spielern und Mojang-Treffern wiederholen.
        NickNameGenerator generator = new NickNameGenerator(new SplittableRandom(3));
        List<String> first = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            first.add(generator.next());
        }
        store.players.add(first.get(0).toLowerCase(Locale.ROOT));
        FakeHttp http = mojang(Set.of(first.get(1)));
        Fill fill = filler(http, settings(true, List.of(A)), 3).fill(4);
        assertEquals(4, fill.added());
        assertTrue(fill.known() >= 1);
        assertTrue(fill.taken() >= 1);
        assertFalse(store.names.containsKey(first.get(0).toLowerCase(Locale.ROOT)), "bekannter Spieler");
        assertFalse(store.names.containsKey(first.get(1).toLowerCase(Locale.ROOT)), "bei Mojang vergeben");
        assertEquals(0, http.count("lookup/name/" + first.get(0)), "bekannte Namen kosten keine Mojang-Anfrage");
    }

    @Test
    void ohneMojangPruefungKeineNamensanfragen() throws InterruptedException {
        FakeHttp http = mojang(Set.of());
        Fill fill = filler(http, settings(false, List.of(A)), 4).fill(10);
        assertEquals(10, fill.added());
        assertEquals(0, http.count("lookup/name/"));
    }

    @Test
    void limitBeendetSchonendUndBehaeltGefundenes() throws InterruptedException {
        AtomicInteger lookups = new AtomicInteger();
        FakeHttp http = new FakeHttp(uri -> {
            if (uri.getHost().startsWith("sessionserver")) {
                return Response.of(200, FakeHttp.profile("q", "w", "s"));
            }
            return lookups.incrementAndGet() > 3 ? FakeHttp.tooMany(120) : Response.of(404, "");
        });
        Fill fill = filler(http, settings(true, List.of(A)), 5).fill(20);
        assertEquals(FillStatus.RATE_LIMITED, fill.status());
        assertEquals(3, fill.added());
        assertEquals(3, store.names.size());
        assertEquals(4, lookups.get(), "nach dem 429 keine weitere Anfrage");
    }

    @Test
    void ohneSkinsKeineNamen() throws InterruptedException {
        FakeHttp http = new FakeHttp(uri -> Response.of(204, ""));
        Fill none = filler(http, settings(true, List.of(A)), 6).fill(5);
        assertEquals(FillStatus.NO_SKINS, none.status());
        assertEquals(0, none.added());
        assertTrue(store.names.isEmpty());
        Fill noSources = filler(http, settings(true, List.of()), 6).fill(5);
        assertEquals(FillStatus.NO_SKINS, noSources.status());
        assertEquals(0, http.count("lookup/name/"));
    }

    @Test
    void mineSkinAlsZweiteQuelle() throws InterruptedException {
        FakeHttp http = new FakeHttp(uri -> uri.getHost().contains("mineskin")
                ? Response.of(200, "{\"skin\":{\"texture\":{\"data\":{\"value\":\"mv\",\"signature\":\"ms\"}}}}")
                : Response.of(404, ""));
        NickPoolSettings settings = new NickPoolSettings(List.of(), false, Duration.ZERO, "schluessel",
                List.of("m1", "m2"), 5, Set.of());
        Fill fill = filler(http, settings, 7).fill(3);
        assertEquals(FillStatus.DONE, fill.status());
        assertEquals(2, fill.skinsAdded());
        assertEquals(3, fill.added());
        assertEquals(Set.of("m1", "m2"), store.skinRefs(NickPoolFiller.SOURCE_MINESKIN));
    }
}
