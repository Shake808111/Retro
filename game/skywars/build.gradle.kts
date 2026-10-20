// skywars: SkyWars: Inseln, Truhen, Mittelinsel, Kits
plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "SkyWars: Inseln, Truhen, Mittelinsel, Kits"

dependencies {
    compileOnly(libs.paper.api)
    // Zur Laufzeit liefert das Plugin paper-core die core-*-Klassen (join-classpath in paper-plugin.yml).
    compileOnly(project(":platform:paper-core"))
    // game-framework wird in das Plugin-Jar eingebettet.
    implementation(project(":game:game-framework"))
}
