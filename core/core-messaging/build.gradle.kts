// core-messaging: netzwerkweite Nachrichten über Valkey Pub/Sub mit JSON-Envelope (Spec 08 §6.12).
plugins {
    `java-library`
}

description = "Typisierte netzwerkweite Nachrichten (Valkey Pub/Sub, Envelope-Codec, Anfrage/Antwort)"

dependencies {
    api(project(":core:core-api"))
    api(project(":core:core-data"))
    // Gson: auf Paper/Velocity von der Plattform geliefert (nicht im Plugin-Jar, siehe Root-Build).
    implementation(libs.gson)

    "integrationTestImplementation"(testFixtures(project(":core:core-data")))
}
