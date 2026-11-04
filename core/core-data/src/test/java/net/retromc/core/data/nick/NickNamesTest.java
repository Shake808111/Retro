package net.retromc.core.data.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.util.PlayerName;
import net.retromc.core.data.punish.SpamFilter;
import org.junit.jupiter.api.Test;

/** Zufallsnamen und Wortfilter des Nick-Pools (Spec 01 §6.11). */
class NickNamesTest {

    private final NickNameFilter filter = new NickNameFilter(new SpamFilter(Set.of()), Set.of("keks"));

    @Test
    void namenSindGueltigVielfaeltigUndWiederholbar() {
        NickNameGenerator generator = new NickNameGenerator(new SplittableRandom(7));
        Set<String> names = new HashSet<>();
        boolean digits = false;
        boolean underscore = false;
        boolean lower = false;
        for (int i = 0; i < 5000; i++) {
            String name = generator.next();
            assertTrue(PlayerName.valid(name), name);
            assertTrue(name.length() >= 3 && name.length() <= 16, name);
            assertFalse(name.contains("88"), name);
            names.add(name);
            digits |= name.matches(".*\\d.*");
            underscore |= name.contains("_");
            lower |= name.equals(name.toLowerCase(java.util.Locale.ROOT));
        }
        assertTrue(names.size() > 4000, "zu wenig Vielfalt: " + names.size());
        assertTrue(digits && underscore && lower);
        NickNameGenerator again = new NickNameGenerator(new SplittableRandom(7));
        NickNameGenerator same = new NickNameGenerator(new SplittableRandom(7));
        assertEquals(List.of(again.next(), again.next()), List.of(same.next(), same.next()));
    }

    @Test
    void wortlistenEnthaltenNichtsGesperrtes() {
        for (List<String> words : List.of(NickNameGenerator.ADJECTIVES, NickNameGenerator.NOUNS,
                NickNameGenerator.FIRST_NAMES, NickNameGenerator.PREFIXES, NickNameGenerator.SUFFIXES)) {
            for (String word : words) {
                assertTrue(word.matches("[A-Za-z]+"), word);
                assertFalse(SpamFilter.containsBlockedWord(word, Set.of()), word);
            }
        }
    }

    @Test
    void filterSperrtWoerterLeetspeakZahlenMarkeUndCaps() {
        assertTrue(filter.allowed("SilentFox42"));
        assertTrue(filter.allowed("Lena_2009"));
        assertFalse(filter.allowed("Ad"), "zu kurz");
        assertFalse(filter.allowed("Fox.de"), "Zeichen");
        assertFalse(filter.allowed("xNaziFox"));
        assertFalse(filter.allowed("N4z1_Wolf"), "Leetspeak");
        assertFalse(filter.allowed("H1tl3r"));
        assertFalse(filter.allowed("Wolf88"), "Zahlencode");
        assertFalse(filter.allowed("Fox1488"));
        assertFalse(filter.allowed("TheAdmin"), "Team-Bezeichnung");
        assertFalse(filter.allowed("ItsNickFox"));
        assertFalse(filter.allowed(BuildInfo.BRAND_ID + "Fox"), "Marke");
        assertFalse(filter.allowed("KeksMonster"), "Zusatzwort aus der Konfiguration");
        assertFalse(filter.allowed("FOXWOLFTIGER"), "Großbuchstaben wie im Chat-Filter");
        assertTrue(SpamFilter.containsBlockedWord("f_u_c_k", Set.of()));
        assertFalse(SpamFilter.containsBlockedWord("Pixel_Panda", Set.of()));
    }
}
