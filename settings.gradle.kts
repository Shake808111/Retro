// Projektgerüst des Netzwerks.
// Modul-Layout nach research_notes/07_technik_stack_recht.md, Abschnitt 6 ("Repo layout").

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // Lädt ein fehlendes JDK 25 automatisch (Gradle-Toolchain-Provisionierung über foojay.io).
    // Version 1.0.0 am 2026-10-20 im Gradle Plugin Portal geprüft (siehe docs/VERSIONEN.md).
    // Hinweis: Plugin-Versionen in settings.gradle.kts können nicht aus libs.versions.toml kommen.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Repositories nur hier zentral; Module dürfen keine eigenen deklarieren.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

// Projektname folgt der Marke aus gradle.properties (brandName), nur Kleinbuchstaben/Ziffern.
rootProject.name = providers.gradleProperty("brandName").get()
    .lowercase()
    .filter { it in 'a'..'z' || it in '0'..'9' }

include(
    // Plattformunabhängiger Kern (reines Java, keine Paper-/Velocity-Abhängigkeit)
    ":core:core-api",
    ":core:core-data",
    ":core:core-messaging",
    ":core:core-i18n",
    ":core:core-stats",
    // Plattform-Kerne
    ":platform:paper-core",
    ":platform:velocity-core",
    // Spielmodi
    ":game:game-framework",
    ":game:bedwars",
    ":game:skywars",
    ":game:survivalgames",
    ":game:cores",
    ":game:ttt",
    ":game:gungame",
    ":game:endergames",
    ":game:masterbuilders",
    ":game:clanwar",
    // Dauerhafte Server
    ":lobby",
    ":citybuild",
    // Eigenständige Dienste
    ":services:controller",
    ":services:api",
    ":services:discord-bot",
    // Werkzeuge
    ":tools:map-exporter",
)
