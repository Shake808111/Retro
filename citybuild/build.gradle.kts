// citybuild: CityBuild: Plots, Wirtschaft, Shops
plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "CityBuild: Plots, Wirtschaft, Shops"

dependencies {
    compileOnly(libs.paper.api)
    // Zur Laufzeit liefert das Plugin paper-core die core-*-Klassen (join-classpath in paper-plugin.yml).
    compileOnly(project(":platform:paper-core"))
}
