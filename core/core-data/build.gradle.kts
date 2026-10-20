// core-data: Zugriff auf PostgreSQL (HikariCP) und Valkey (Lettuce).
plugins {
    `java-library`
}

description = "Datenzugriff: PostgreSQL-Pool und Valkey-Verbindungen, Schlüssel-Konventionen"

dependencies {
    api(project(":core:core-api"))
    api(libs.hikaricp)
    api(libs.lettuce)
    runtimeOnly(libs.postgresql)
}
