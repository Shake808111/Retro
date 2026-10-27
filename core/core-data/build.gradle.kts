// core-data: Zugriff auf PostgreSQL (HikariCP) und Valkey (Lettuce).
// testFixtures: gemeinsame Testcontainer für alle Module (Spec 08 §1.1, §6.16).
plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Datenzugriff: PostgreSQL-Pool und Valkey-Verbindungen, Schlüssel-Konventionen"

dependencies {
    api(project(":core:core-api"))
    api(libs.hikaricp)
    api(libs.lettuce)
    runtimeOnly(libs.postgresql)

    // TestDatabases: geteilte Container (postgres:18.6-alpine, valkey/valkey:8.1.10-alpine) + Flyway.
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.testcontainers)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.junit.jupiter)
    testFixturesApi(libs.flyway.core)
    testFixturesRuntimeOnly(libs.flyway.postgresql)
    testFixturesRuntimeOnly(libs.postgresql)
    testFixturesRuntimeOnly(libs.slf4j.simple)

    "integrationTestImplementation"(testFixtures(project))
}

// Flyway-Migrationen aus db/migrations als Klassenpfad-Ressourcen "migrations/..." – wie im Controller
// (services/controller: Migrations.LOCATION = "classpath:migrations").
sourceSets.named("testFixtures") {
    resources.srcDir(layout.settingsDirectory.dir("db"))
}
