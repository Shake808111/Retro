// core-api: reines Java, keine Plattform-Abhängigkeiten.
plugins {
    `java-library`
}

description = "Plattformunabhängige Typen, IDs und Konstanten des Netzwerks"

dependencies {
    // @Nullable/@NullMarked (Spec 00 §4.1); Annotationen sind Teil der öffentlichen API.
    api(libs.jspecify)
}

// BuildInfo.java aus gradle.properties erzeugen (Marke + Version als Compile-Zeit-Konstanten,
// damit sie z. B. in der Velocity-Annotation @Plugin verwendet werden können).
val generateBuildInfo = tasks.register<Sync>("generateBuildInfo") {
    val props = mapOf(
        "brandName" to project.extra["brandName"].toString(),
        "brandId" to project.extra["brandId"].toString(),
        "version" to project.version.toString(),
    )
    inputs.properties(props)
    from("src/template/java")
    into(layout.buildDirectory.dir("generated/sources/buildinfo/java/main"))
    expand(props)
}

sourceSets.main {
    java.srcDir(generateBuildInfo)
}
