// api: REST-Schnittstelle für Website und Staff-Werkzeuge (Platzhalter).
plugins {
    application
}

description = "Web-API (Platzhalter): Statistiken, Profile, Bestenlisten, Entbannungsanträge"

dependencies {
    implementation(project(":core:core-api"))
}

application {
    mainClass = "dev.netzwerk.api.ApiApplication"
}
