// lobby: Lobby: Navigator, NPC-Joinsystem, Hologramme, tägliche Belohnung, Jump & Run
plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "Lobby: Navigator, NPC-Joinsystem, Hologramme, tägliche Belohnung, Jump & Run"

dependencies {
    compileOnly(libs.paper.api)
    // Zur Laufzeit liefert das Plugin paper-core die core-*-Klassen (join-classpath in paper-plugin.yml).
    compileOnly(project(":platform:paper-core"))
}
