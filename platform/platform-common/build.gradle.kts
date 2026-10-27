// platform-common: plattformfreie Bausteine, die paper-core und velocity-core gemeinsam nutzen
// (Feature-Lebenszyklus, Service-Registry, Umgebungs-Konfiguration, Verbindungsaufbau, Executor,
// Bridge-Codec, Befehlsfehler). Kein Paper-/Velocity-Code: alles ohne Server testbar (Spec 00 §4.1).
// Wird in beide Plugin-Jars eingebettet (Shadow).
plugins {
    `java-library`
}

description = "Plattformfreie Bausteine der Kern-Plugins für Paper und Velocity"

dependencies {
    api(project(":core:core-api"))
    api(project(":core:core-data"))
    api(project(":core:core-messaging"))
    api(project(":core:core-i18n"))
    // Befehle (Spec 01 §1, Spec 02 §1): cloud-core ist plattformfrei; cloud-paper/-velocity bringen es ebenfalls mit.
    api(libs.cloud.core)

    // Adventure/MiniMessage liefern Paper und Velocity zur Laufzeit (wie in core-i18n).
    compileOnly(platform(libs.adventure.bom))
    compileOnly(libs.adventure.text.minimessage)

    testImplementation(platform(libs.adventure.bom))
    testImplementation(libs.adventure.text.minimessage)
    testImplementation(libs.adventure.text.serializer.plain)
}
