// velocity-core: Kern-Plugin auf dem Velocity-Proxy.
// Keine Flyway-Abhängigkeit: Migrationen laufen nur über den Compose-Dienst "migrate" bzw.
// "controller migrate" (Spec 08 §3 C4, §6.15).
plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.velocity)
}

description = "Netzwerk-Kern für den Velocity-Proxy"

dependencies {
    compileOnly(libs.velocity.api)
    // Der Annotation-Prozessor erzeugt velocity-plugin.json aus @Plugin.
    annotationProcessor(libs.velocity.api)
    // LuckPerms-Velocity ist Pflicht-Plugin (@Dependency) und liefert die API zur Laufzeit (Spec 01 §1).
    compileOnly(libs.luckperms.api)

    implementation(project(":platform:platform-common"))
    implementation(project(":core:core-api"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-messaging"))
    implementation(project(":core:core-i18n"))
    implementation(libs.cloud.velocity)
}
