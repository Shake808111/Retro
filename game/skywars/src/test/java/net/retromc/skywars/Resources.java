package net.retromc.skywars;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.retromc.skywars.chest.LootTables;
import net.retromc.skywars.kit.KitCatalog;

/** Mitgelieferte Kataloge aus {@code src/main/resources} für die Tests. */
final class Resources {

    private Resources() {
    }

    static Reader open(String name) {
        return new InputStreamReader(Objects.requireNonNull(Resources.class.getResourceAsStream("/" + name), name),
                StandardCharsets.UTF_8);
    }

    static KitCatalog kits() throws java.io.IOException {
        try (Reader reader = open("kits/skywars.yml")) {
            return KitCatalog.load(reader);
        }
    }

    static LootTables loot() {
        return LootTables.load(name -> open("loot/" + name + ".yml"));
    }
}
