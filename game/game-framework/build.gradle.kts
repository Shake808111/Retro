// game-framework: gemeinsame Grundlage aller Spielmodi (Bibliothek, wird in jedes Modus-Jar eingebettet).
// Spec 03 §1: compileOnly paper-api und paper-core (bringt core-api, core-messaging, core-i18n, core-data mit).
plugins {
    `java-library`
}

description = "Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke, Kampfprofile"

dependencies {
    // Zur Laufzeit liefern Paper und das Plugin paper-core diese Klassen.
    compileOnly(libs.paper.api)
    compileOnly(project(":platform:paper-core"))

    // Reine Logikklassen (Countdown, Teams, Forcemap, Herzzeile …) laufen ohne Server.
    testImplementation(libs.paper.api)
    testImplementation(project(":platform:paper-core"))
    testImplementation(libs.gson)
}
