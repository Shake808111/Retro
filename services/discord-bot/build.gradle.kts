// discord-bot: Discord-Anbindung (Platzhalter, JDA geplant - Version im Katalog geprüft).
plugins {
    application
}

description = "Discord-Bot (Platzhalter): Kontoverknüpfung, Rollen-Abgleich"

dependencies {
    implementation(project(":core:core-api"))
}

application {
    mainClass = "dev.netzwerk.discord.bot.DiscordBotApplication"
}
