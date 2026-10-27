// paper-core: Kern-Plugin auf jedem Paper-Server (Lobby, Spielserver, CityBuild).
// Bündelt die core-*-Module im Shadow-Jar; andere Paper-Plugins binden es per
// paper-plugin.yml (join-classpath) ein und kompilieren nur dagegen (compileOnly).
// Keine Flyway-Abhängigkeit: Migrationen laufen nur über den Compose-Dienst "migrate" bzw.
// "controller migrate" (Spec 08 §3 C4, §6.15), nie auf Spiel- oder Lobbyservern.
plugins {
    `java-library`
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "Netzwerk-Kern für Paper-Server"

dependencies {
    compileOnly(libs.paper.api)
    // LuckPerms ist Pflicht-Plugin (paper-plugin.yml, join-classpath) und liefert die API zur Laufzeit.
    compileOnly(libs.luckperms.api)

    api(project(":platform:platform-common"))
    api(project(":core:core-api"))
    api(project(":core:core-data"))
    api(project(":core:core-messaging"))
    api(project(":core:core-i18n"))
    // Befehle: Spielmodule registrieren über den Command-Manager des Kerns (Typen sind Teil der API).
    api(libs.cloud.paper)
    // GUIs: nur Unterbau des eigenen GUI-API (Spec 02 §5), daher nicht Teil der öffentlichen API.
    implementation(libs.inventoryframework)

    // Plattformfreie Tests (Layout, Paging, Item-Logik): Adventure ohne Server.
    testImplementation(platform(libs.adventure.bom))
    testImplementation(libs.adventure.text.minimessage)
    testImplementation(libs.adventure.text.serializer.plain)
}
