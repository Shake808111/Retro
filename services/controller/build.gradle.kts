// controller: Queue/Matchmaking, Server-Registry, Agones-Allocation, Datenbank-Migrationen.
plugins {
    application
}

description = "Netzwerk-Controller: Queues, Matchmaking, Server-Registry, Migrationen"

dependencies {
    implementation(project(":core:core-api"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-messaging"))
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass = "net.retromc.controller.ControllerApplication"
}

// Flyway-Migrationen aus db/migrations als Klassenpfad-Ressourcen "migrations/..." mitliefern.
sourceSets.main {
    resources.srcDir(layout.settingsDirectory.dir("db"))
}
