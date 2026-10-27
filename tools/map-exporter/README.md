# tools/map-exporter – Karten-Export

**Gradle-Pfad:** `:tools:map-exporter` · **Paket:** `net.retromc.map.exporter` · **Einstieg:** `MapExporterApplication` (Java-Anwendung) · **Status:** Gerüst (Stub)

## Verantwortung

Prüft Kartenordner (Pflichtmarker vorhanden) und packt sie später als versionierte `.zip` für den Objektspeicher (S3-kompatibel), aus dem ein Init-Container die Karte des Pods lädt (Notiz 07, Abschnitte 3, 5 und 6). Karten werden auf einem separaten Builder-Server mit FAWE/WorldEdit gebaut.

## Welches Vorbild-Feature wird nachgebaut?

Kein direktes Spieler-Feature; Werkzeug für die Karten aller Spielmodi. Wie Vorbild intern Karten verwaltet, ist nicht dokumentiert (LÜCKE).


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
