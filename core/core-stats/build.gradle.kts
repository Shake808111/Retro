// core-stats: reines Java ohne externe Abhängigkeiten (Spec 02 §1).
plugins {
    `java-library`
}

description = "Statistik-Logik: Punkteformeln, K/D, Siegesquote, Zeitfenster, Ranking-Score, /stats-Ausgabe"

dependencies {
    api(project(":core:core-api"))
}
