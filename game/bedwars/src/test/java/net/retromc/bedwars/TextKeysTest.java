package net.retromc.bedwars;

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
import net.retromc.bedwars.shop.ShopCatalog;
import net.retromc.bedwars.shop.ShopItem;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.bedwars.trap.TrapType;
import net.retromc.bedwars.upgrade.UpgradeType;
import org.junit.jupiter.api.Test;

/**
 * Key-Scan: Spec 03 §9 #24 (alle {@code common.*}-Schlüssel aus §7.2 existieren und werden benutzt) und Spec 04
 * §9 #18 (alle BedWars-Schlüssel aus §7 in de_DE und en_US), dazu jeder im Code genutzte Schlüssel im Katalog.
 */
class TextKeysTest {

    private static final Path ROOT = Path.of("../..");
    private static final Pattern LITERAL = Pattern.compile("\"((?:common|bw|game|round|team|tips|guide|title)\\."
            + "[a-z0-9_.]*[a-z0-9_])\"");

    private static Properties properties(String path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(ROOT.resolve(path)),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> literals() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String module : new String[] {"game/game-framework", "game/bedwars"}) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src/main/java"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        keys.add(matcher.group(1));
                    }
                }
            }
        }
        return keys;
    }

    private static boolean used(Set<String> literals, String key) {
        return literals.contains(key) || (key.endsWith(".one") && literals.contains(key.substring(0,
                key.length() - ".one".length())));
    }

    @Test
    void spec03KeysExistierenUndWerdenBenutzt() throws IOException {
        Properties de = properties("core/core-i18n/src/main/resources/i18n/messages.properties");
        Set<String> literals = literals();
        Set<String> unused = new TreeSet<>();
        Properties spec = properties("core/core-i18n/src/test/resources/spec/spec03.properties");
        for (String key : spec.stringPropertyNames()) {
            assertTrue(de.containsKey(key), "de_DE fehlt " + key);
            if (!used(literals, key)) {
                unused.add(key);
            }
        }
        assertEquals(Set.of(), unused, "Spec-03-Schlüssel ohne Verwendung");
    }

    @Test
    void spec04KeysInDeutschUndEnglisch() throws IOException {
        Properties de = properties("core/core-i18n/src/main/resources/i18n/messages.properties");
        Properties en = properties("core/core-i18n/src/main/resources/i18n/messages_en.properties");
        for (String file : new String[] {"spec04", "spec04-ergaenzungen"}) {
            for (String key : properties("core/core-i18n/src/test/resources/spec/" + file + ".properties")
                    .stringPropertyNames()) {
                assertTrue(de.containsKey(key), "de_DE fehlt " + key);
                assertTrue(en.containsKey(key), "en_US fehlt " + key);
            }
        }
    }

    @Test
    void jederSchluesselImCodeStehtImKatalog() throws IOException {
        Properties de = properties("core/core-i18n/src/main/resources/i18n/messages.properties");
        Set<String> missing = new TreeSet<>();
        for (String key : literals()) {
            if (!de.containsKey(key) && !key.equals("team.short") && !key.equals("game.variant")) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamischeSchluesselDesShops() throws IOException {
        Properties de = properties("core/core-i18n/src/main/resources/i18n/messages.properties");
        ShopCatalog catalog;
        try (Reader reader = new InputStreamReader(TextKeysTest.class.getResourceAsStream("/shop.yml"),
                StandardCharsets.UTF_8)) {
            catalog = ShopCatalog.load(reader, false);
        }
        Set<String> missing = new TreeSet<>();
        for (ShopItem item : catalog.items()) {
            check(de, missing, item.nameKey());
            if (item.lore()) {
                check(de, missing, item.loreKey());
            }
        }
        check(de, missing, "bw.item.sandstone");
        for (String category : ShopCatalog.CATEGORIES) {
            check(de, missing, "bw.shop.cat." + category);
            check(de, missing, "bw.shop.cat." + category + ".lore");
        }
        for (UpgradeType type : UpgradeType.values()) {
            check(de, missing, type.nameKey());
            check(de, missing, type.nameKey() + ".item");
            check(de, missing, type.nameKey() + ".desc");
        }
        for (TrapType trap : TrapType.values()) {
            check(de, missing, trap.nameKey());
            check(de, missing, trap.nameKey() + ".item");
            check(de, missing, trap.nameKey() + ".desc");
        }
        for (Resource resource : Resource.values()) {
            check(de, missing, resource.nameKey());
            check(de, missing, "bw.resources." + resource.id());
            check(de, missing, "bw.resources." + resource.id() + ".one");
        }
        for (GoldVote.Rate rate : GoldVote.Rate.values()) {
            check(de, missing, "bw.goldvote.item." + rate.id());
            check(de, missing, "bw.goldvote.rate." + rate.id());
        }
        for (String kind : new String[] {"weapon", "pickaxe", "blocks", "bow", "potion"}) {
            check(de, missing, "bw.settings." + kind);
        }
        for (String mode : new String[] {"bedwars", "bedwars_classic"}) {
            check(de, missing, "guide." + mode + ".title");
            check(de, missing, "guide." + mode + ".page.1");
            for (int i = 1; i <= 5; i++) {
                check(de, missing, "tips." + mode + "." + i);
            }
        }
        check(de, missing, "bw.board.diamond.max");
        check(de, missing, "bw.board.gold.max");
        assertEquals(Set.of(), missing);
    }

    private static void check(Properties catalog, Set<String> missing, String key) {
        if (!catalog.containsKey(key)) {
            missing.add(key);
        }
    }
}
