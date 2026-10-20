// velocity-core: Kern-Plugin auf dem Velocity-Proxy.
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

    implementation(project(":core:core-api"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-messaging"))
    implementation(project(":core:core-i18n"))
}
