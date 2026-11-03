// core-stats: reines Java ohne externe Abhängigkeiten (Spec 02 §1).
plugins {
    `java-library`
}

description = "Statistik-Logik: Punkteformeln, K/D, Siegesquote, Zeitfenster, Ranking-Score, /stats-Ausgabe"

dependencies {
    api(project(":core:core-api"))

    // Abnahme Spec 02 §9 #1: Formate je Oberfläche liegen in core-i18n (Formats), nur im Test gebraucht.
    testImplementation(project(":core:core-i18n"))
}
