// paper-core: Kern-Plugin auf jedem Paper-Server (Lobby, Spielserver, CityBuild).
// Bündelt die core-*-Module im Shadow-Jar; andere Paper-Plugins binden es per
// paper-plugin.yml (join-classpath) ein und kompilieren nur dagegen (compileOnly).
plugins {
    `java-library`
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "Netzwerk-Kern für Paper-Server"

dependencies {
    compileOnly(libs.paper.api)
    api(project(":core:core-api"))
    api(project(":core:core-data"))
    api(project(":core:core-messaging"))
    api(project(":core:core-i18n"))
}
