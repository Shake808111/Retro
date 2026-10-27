// core-i18n: Textkatalog (MiniMessage) de_DE (Standard), en_US, bar, gsw und Formate.
plugins {
    `java-library`
}

description = "Texte im MiniMessage-Format, Deutsch zuerst, und Zahl-/Datumsformate"

dependencies {
    api(project(":core:core-api"))

    // Adventure/MiniMessage liefern Paper und Velocity zur Laufzeit; hier nur zum Kompilieren.
    // Messages, Formats und Language kommen ohne Adventure aus (z. B. für Dienste ohne Plattform).
    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.text.minimessage)

    testImplementation(platform(libs.adventure.bom))
    testImplementation(libs.adventure.text.minimessage)
    testImplementation(libs.adventure.text.serializer.legacy)
    testImplementation(libs.adventure.text.serializer.plain)
}
