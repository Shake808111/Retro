# services/discord-bot – Discord-Bot (Platzhalter)

**Gradle-Pfad:** `:services:discord-bot` · **Paket:** `net.retromc.discord.bot` · **Einstieg:** `DiscordBotApplication` (Java-Anwendung) · **Status:** Gerüst (Stub)

## Verantwortung

JDA-Bot nach Notiz 07, Abschnitt 7:
1. `/discord link` im Spiel erzeugt einen 6-stelligen Einmalcode (Valkey, TTL 10 Minuten);
2. `/link <code>` in Discord;
3. der Bot schreibt `integ.discord_link` und vergibt Rollen aus LuckPerms-Gruppen;
4. `/unlink` löscht die Verknüpfung (DSGVO).

## Welches Vorbild-Feature wird nachgebaut?

Vorbild wickelt Support-Tickets über den offiziellen Discord ab (Notiz 06, Abschnitt 2) und bietet eine Discord-Kontoverknüpfung, die der Nutzer per **Bot-Befehl** startet; genutzt für Kontoabgleich, **Rollen- und Nickname-Synchronisation** und linkabhängige Rechte; Trennen per Bot-Befehl oder in den Kontoeinstellungen, beim Verlassen des Servers werden die Daten bereinigt (Notiz 06, Abschnitt 4). Ein Spielbefehl `/discord` ist beim Vorbild **nicht belegt** (Notiz 05, Abschnitt 7) – der Einmalcode-Ablauf oben ist die Variante aus Notiz 07.


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
