package net.retromc.map.exporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prüft einen exportierten Kartenordner. Eigene Konvention (Notiz 07, Abschnitte 5 und 6):
 * Anvil-Weltordner mit {@code region/} und einer Kartenbeschreibung {@code map.json}
 * (Spawns, Betten, Spawner, Cores, Truhen, Regionen - Inhalt wird beim Start der Umsetzung festgelegt).
 */
public final class MapValidator {

    public static final String MAP_CONFIG = "map.json";
    public static final String REGION_DIR = "region";

    private MapValidator() {
    }

    /** @return Liste der Probleme; leer, wenn der Ordner gültig ist */
    public static List<String> validate(Path mapDir) {
        List<String> problems = new ArrayList<>();
        if (!Files.isDirectory(mapDir)) {
            problems.add("Kein Ordner: " + mapDir);
            return problems;
        }
        if (!Files.isRegularFile(mapDir.resolve(MAP_CONFIG))) {
            problems.add("Datei fehlt: " + MAP_CONFIG);
        }
        if (!Files.isDirectory(mapDir.resolve(REGION_DIR))) {
            problems.add("Ordner fehlt: " + REGION_DIR + "/");
        }
        return problems;
    }
}
