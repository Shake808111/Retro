package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.retromc.skywars.challenge.Challenge;
import net.retromc.skywars.kit.KitCatalog;
import net.retromc.skywars.kit.KitDef;
import net.retromc.skywars.kit.KitItem;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #18: alle SkyWars-Schlüssel aus §7.2 (Soll-Datei {@code spec04.properties}) und die Ergänzungen in
 * de_DE und en_US; jeder im Code und in den Katalogen ({@code kits/skywars.yml}) genutzte Schlüssel steht im Katalog.
 */
class TextKeysTest {

    private static final Path ROOT = Path.of("../..");
    private static final Pattern LITERAL = Pattern.compile("\"((?:sw|common|game|round|tips|guide|title|chat)\\."
            + "[a-z0-9_.]*[a-z0-9_])\"");

    private static Properties properties(String path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(ROOT.resolve(path)),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Properties german() throws IOException {
        return properties("core/core-i18n/src/main/resources/i18n/messages.properties");
    }

    @Test
    void spec04SkyWarsKeysInDeutschUndEnglisch() throws IOException {
        Properties de = german();
        Properties en = properties("core/core-i18n/src/main/resources/i18n/messages_en.properties");
        Set<String> skywars = new TreeSet<>();
        for (String file : new String[] {"spec04", "spec04-ergaenzungen"}) {
            for (String key : properties("core/core-i18n/src/test/resources/spec/" + file + ".properties")
                    .stringPropertyNames()) {
                if (key.startsWith("sw.")) {
                    skywars.add(key);
                }
                assertTrue(de.containsKey(key), "de_DE fehlt " + key);
                assertTrue(en.containsKey(key), "en_US fehlt " + key);
            }
        }
        // Zeilen aus §7.2 (Spec zählt 44 Zeilen, einige mit zwei Schlüsseln oder Einzahl)
        for (String key : new String[] {"sw.join", "sw.quit.lobby", "sw.teaming", "sw.tip.shield", "sw.tip.xp",
            "sw.join.online", "sw.join.map", "sw.countdown", "sw.team.joined", "sw.teleported", "sw.kit.announce",
            "sw.kit.selected", "sw.kit.bought", "sw.kit.nocoins", "sw.phase.build", "sw.protection",
            "sw.phase.fight", "sw.border.in", "sw.border.twoteams", "sw.border.start", "sw.event", "sw.kill.kit",
            "sw.kill", "sw.death", "sw.life", "sw.remaining", "sw.challenge.failed", "sw.challenge.done",
            "sw.bossbar.challenge", "sw.ability.ready", "sw.ability.cooldown", "sw.ability.protection",
            "sw.ability.uses", "sw.ability.empty", "sw.win", "sw.title.win", "sw.title.start", "sw.end.restart",
            "sw.chat.lobby", "sw.chat.dead", "sw.coins", "sw.mapvote", "sw.mapvote.result",
            "sw.achievement.overkill", "sw.round.chests", "sw.round.blocks", "sw.round.challenge"}) {
            assertTrue(skywars.contains(key), "Spec-Zeile fehlt in den Soll-Dateien: " + key);
        }
    }

    @Test
    void jederSchluesselImCodeStehtImKatalog() throws IOException {
        Properties de = german();
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("game/skywars/src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!de.containsKey(key) && !key.equals("game.variant")) {
                        missing.add(key);
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamischeSchluessel() throws IOException {
        Properties de = german();
        Set<String> missing = new TreeSet<>();
        KitCatalog catalog = Resources.kits();
        for (KitDef kit : catalog.kits()) {
            check(de, missing, kit.nameKey());
            if (kit.ability() != null) {
                check(de, missing, kit.descKey());
                if (kit.ability().item() != null && kit.ability().item().nameKey() != null) {
                    check(de, missing, kit.ability().item().nameKey());
                }
            }
            for (KitItem item : kit.items()) {
                if (item.nameKey() != null) {
                    check(de, missing, item.nameKey());
                }
            }
        }
        for (Challenge challenge : Challenge.values()) {
            check(de, missing, challenge.textKey());
        }
        check(de, missing, "sw.event.chestrefill");
        for (String mode : new String[] {"skywars", "skywars_classic"}) {
            check(de, missing, "mode." + mode);
            check(de, missing, "guide." + mode + ".title");
            check(de, missing, "guide." + mode + ".page.1");
            check(de, missing, "tips." + mode + ".1");
        }
        for (String plural : new String[] {"sw.countdown", "sw.protection", "sw.border.in", "sw.remaining",
            "sw.ability.ready", "sw.ability.cooldown", "sw.ability.uses", "sw.end.restart"}) {
            check(de, missing, plural + ".one");
        }
        assertEquals(Set.of(), missing);
    }

    private static void check(Properties catalog, Set<String> missing, String key) {
        if (!catalog.containsKey(key)) {
            missing.add(key);
        }
    }
}
