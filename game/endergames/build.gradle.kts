// endergames: EnderGames: SG-artiges FFA mit Zufalls-Kit
plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "EnderGames: SG-artiges FFA mit Zufalls-Kit"

dependencies {
    compileOnly(libs.paper.api)
    // Zur Laufzeit liefert das Plugin paper-core die core-*-Klassen (join-classpath in paper-plugin.yml).
    compileOnly(project(":platform:paper-core"))
    // game-framework wird in das Plugin-Jar eingebettet.
    implementation(project(":game:game-framework"))
}
