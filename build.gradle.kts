// Root-Build: gemeinsame Konventionen für alle Module.
// - Java-Toolchain 25 (Paper 26.x und Velocity 4.3 verlangen Java 25, siehe docs/VERSIONEN.md)
// - Marke aus gradle.properties -> paper-plugin.yml und BuildInfo
// - Shadow-Jar = das auslieferbare Plugin-Jar
// - run-paper/run-velocity für lokale Testserver
// - Aufgabe "devPlugins" für docker/docker-compose.dev.yml

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runvelocity.task.RunVelocity

plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
    alias(libs.plugins.run.velocity) apply false
}

// ---------------------------------------------------------------------
// Marke (einzige Quelle: gradle.properties -> brandName)
// ---------------------------------------------------------------------
val brandName: String = providers.gradleProperty("brandName").get().trim()
require(brandName.matches(Regex("[A-Za-z0-9 _.-]+"))) {
    "brandName '$brandName' ist ungültig: erlaubt sind nur A-Z, a-z, 0-9, Leerzeichen, '_', '.', '-' " +
        "(Vorgabe für Paper-Plugin-Namen)."
}
val brandId: String = brandName.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
require(brandId.isNotEmpty() && brandId.first() in 'a'..'z') {
    "Aus brandName '$brandName' entsteht keine gültige ID (muss mit einem Buchstaben beginnen; " +
        "Vorgabe für Velocity-Plugin-IDs)."
}
val paperApiVersion: String = providers.gradleProperty("paperApiVersion").get()
val minecraftVersion: String = providers.gradleProperty("minecraftVersion").get()
val velocityVersion: String = providers.gradleProperty("velocityVersion").get()

allprojects {
    extra["brandName"] = brandName
    extra["brandId"] = brandId
}

// Attribute, mit denen ein Modul das Shadow-Jar (statt des normalen Jars) eines anderen Moduls anfordert.
fun Configuration.requestShadowJar() {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    // -----------------------------------------------------------------
    // Java-Konventionen (für jedes Modul mit java / java-library / application)
    // -----------------------------------------------------------------
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(25)
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = 25
            options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        tasks.withType<Jar>().configureEach {
            manifest.attributes(
                "Implementation-Title" to "$brandName ${project.name}",
                "Implementation-Version" to project.version.toString(),
            )
        }
        tasks.named<ProcessResources>("processResources") {
            val props = mapOf(
                "brandName" to brandName,
                "brandId" to brandId,
                "version" to project.version.toString(),
                "paperApiVersion" to paperApiVersion,
            )
            inputs.properties(props)
            filesMatching("paper-plugin.yml") {
                expand(props)
            }
        }
        dependencies {
            "testImplementation"(platform(libs.junit.bom))
            "testImplementation"(libs.junit.jupiter)
            "testRuntimeOnly"(libs.junit.platform.launcher)
        }
    }

    // Dienste: Konsolenausgabe immer UTF-8 (Umlaute), unabhängig von der Systemsprache.
    plugins.withId("application") {
        extensions.configure<JavaApplication> {
            applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
        }
    }

    // -----------------------------------------------------------------
    // Plugin-Jars: das Shadow-Jar ist das Artefakt, das auf den Server kommt.
    // -----------------------------------------------------------------
    plugins.withId("com.gradleup.shadow") {
        tasks.named<Jar>("jar") {
            archiveClassifier = "plain"
        }
        tasks.named<ShadowJar>("shadowJar") {
            archiveClassifier = ""
            // Service-Dateien (z. B. java.sql.Driver) zusammenführen statt Duplikate zu verwerfen
            // (Empfehlung der Shadow-Dokumentation "Handling duplicates strategy").
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            mergeServiceFiles()
            filesNotMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
            dependencies {
                // Netty und SLF4J liefern Paper und Velocity selbst mit.
                exclude(dependency("io.netty:.*"))
                exclude(dependency("org.slf4j:.*"))
            }
        }
    }

    // -----------------------------------------------------------------
    // Lokaler Paper-Testserver: ./gradlew :<modul>:runServer
    // Jedes Paper-Plugin außer paper-core bekommt das paper-core-Jar dazu.
    // -----------------------------------------------------------------
    plugins.withId("xyz.jpenilla.run-paper") {
        val corePlugin = configurations.create("devCorePlugin") { requestShadowJar() }
        if (path != ":platform:paper-core") {
            dependencies { corePlugin(project(":platform:paper-core")) }
        }
        tasks.named<RunServer>("runServer") {
            minecraftVersion(minecraftVersion)
            pluginJars(corePlugin)
        }
    }

    plugins.withId("xyz.jpenilla.run-velocity") {
        tasks.named<RunVelocity>("runVelocity") {
            velocityVersion(velocityVersion)
        }
    }
}

// ---------------------------------------------------------------------
// ./gradlew devPlugins  ->  docker/dev-plugins/{proxy,lobby,game-bedwars}
// Wird von docker/docker-compose.dev.yml als /plugins eingebunden.
// ---------------------------------------------------------------------
val devProxyPlugins = configurations.create("devProxyPlugins") { requestShadowJar() }
val devLobbyPlugins = configurations.create("devLobbyPlugins") { requestShadowJar() }
val devBedwarsPlugins = configurations.create("devBedwarsPlugins") { requestShadowJar() }

dependencies {
    devProxyPlugins(project(":platform:velocity-core"))
    devLobbyPlugins(project(":platform:paper-core"))
    devLobbyPlugins(project(":lobby"))
    devBedwarsPlugins(project(":platform:paper-core"))
    devBedwarsPlugins(project(":game:bedwars"))
}

tasks.register<Sync>("devPlugins") {
    group = "netzwerk"
    description = "Kopiert die Plugin-Jars für die Docker-Entwicklungsumgebung nach docker/dev-plugins/."
    into(layout.projectDirectory.dir("docker/dev-plugins"))
    into("proxy") { from(devProxyPlugins) }
    into("lobby") { from(devLobbyPlugins) }
    into("game-bedwars") { from(devBedwarsPlugins) }
}
