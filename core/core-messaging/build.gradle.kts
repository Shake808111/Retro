// core-messaging: netzwerkweite Nachrichten über Valkey Pub/Sub (später Streams oder NATS).
plugins {
    `java-library`
}

description = "Typisierte netzwerkweite Nachrichten (Valkey Pub/Sub)"

dependencies {
    api(project(":core:core-api"))
    implementation(libs.lettuce)
}
