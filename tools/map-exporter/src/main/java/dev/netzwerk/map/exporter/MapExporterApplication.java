package dev.netzwerk.map.exporter;

import java.nio.file.Path;
import java.util.List;

/** Aufruf: {@code ./gradlew :tools:map-exporter:run --args=<kartenordner>}. Packen/Hochladen folgt später. */
public final class MapExporterApplication {

    private MapExporterApplication() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Aufruf: map-exporter <kartenordner>");
            System.exit(2);
        }
        List<String> problems = MapValidator.validate(Path.of(args[0]));
        if (problems.isEmpty()) {
            System.out.println("Karte gültig: " + args[0]);
        } else {
            problems.forEach(problem -> System.err.println("FEHLER: " + problem));
            System.exit(1);
        }
    }
}
