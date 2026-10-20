// game-framework: gemeinsame Grundlage aller Spielmodi (Bibliothek, wird in jedes Modus-Jar eingebettet).
plugins {
    `java-library`
}

description = "Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke, Kampfprofile"

dependencies {
    // Zur Laufzeit liefern Paper und das Plugin paper-core diese Klassen.
    compileOnly(libs.paper.api)
    compileOnly(project(":core:core-api"))
}
