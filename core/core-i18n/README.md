# core/core-i18n – Texte, Sprachen und Formate

**Gradle-Pfad:** `:core:core-i18n` · **Paket:** `net.retromc.core.i18n` · **Einstieg:** `Messages`, `MessageRenderer`, `Formats`, `Language` · **Status:** Phase 1 (Katalog Spec 01/02/08)

## Verantwortung

Alle Spielertexte im MiniMessage-Format (Spec 00 §4.3), **Deutsch zuerst**, und die Zahl-, Dauer- und Datumsformate
(Spec 02 §6.8, Spec 01 §6.10). Plattformfrei: Adventure/MiniMessage nur `compileOnly`, Paper und Velocity liefern
es zur Laufzeit. `Messages`, `Formats` und `Language` laufen auch ganz ohne Adventure (Dienste, Discord-Bot).

| Datei | Inhalt |
|---|---|
| `i18n/messages.properties` | `de_DE`, Basis und Rückfallsprache, jeder Schlüssel |
| `i18n/messages_en.properties` | `en_US`, Pflicht: jeder Schlüssel, gleiche Farben/Platzhalter/Zeilen |
| `i18n/messages_bar.properties`, `_gsw` | Dialekte Boarisch/Schwiizerdütsch (Spec 01 §6.14, O29); Spielertexte übersetzt, Rest aus `de_DE` |
| `src/test/resources/spec/*.properties` | Soll-Texte aus Spec 01/02/08 §7 in §-Notation; der Katalogtest vergleicht Zeichen für Zeichen |

## Notation

- Spec-`§x` → `<farbe>`, Spec-`{x}` → `<x>` in snake_case (`{modeColor}` → `<mode_color>`).
- `<brand>`/`<brand_upper>` = Marke aus `gradle.properties`; nie hart codieren. `<p:friends>` = Text von `prefix.friends`.
- `<name>` = Name in Rangfarbe (Komponente), `<player>` = reiner Text, `<..._color>` = Farb-Platzhalter (`Placeholder.styling`).
- `<online:'…':'…'>` = Auswahl (`Formatter.booleanChoice`), `<names:'<gray>, '>` = Namensliste (`Formatter.joining`).
- `<newline>` trennt Zeilen; `\\<` ist ein wörtliches `<`; führende/folgende Leerzeichen als `\u0020`.
- Varianten: `<key>.one` = Einzahl (`Messages.plural`), `<key>.legacy` = Fassung für `UiEra.LEGACY`.

## Benutzung

```java
Component c = MessageRenderer.render(locale, "friend.online",
        Placeholder.component("name", coloredName));          // Paper/Velocity
String raw = Messages.raw(locale, "screen.maintenance");        // MiniMessage-Rohtext
String txt = Messages.plain(locale, "bot.link.ok", Map.of("name", discordName)); // Discord/API
String kd  = Formats.of(locale).statsKd(314, 28);               // "11.21"
Language l = Language.forClientLocale("de_at");                // erster Join → de_DE
```

Neu laden (`/netreload`): `Messages.reload(verzeichnis)` legt gleichnamige Dateien aus dem Verzeichnis über die Bündel.

## Welches Vorbild-Feature wird nachgebaut?

<<<<<<< HEAD
Die Sprachwahl über `/language` mit Deutsch, Englisch und den Dialekten (Notiz 05, Abschnitt 7; Spec 01 §6.14).
Alle Texte sind eigene Formulierungen nach Spalte „Standardtext“ der Specs; Originaltexte werden nicht ausgeliefert.

Quellen: `docs/spec/01_proxy_social.md` §7, `docs/spec/02_core_ui_stats.md` §6.8/§6.13/§7,
`docs/spec/08_infrastruktur_dienste.md` §7.
=======
Die Sprachwahl Deutsch/Englisch, beim Vorbild über `/language` (Notiz 05, Abschnitt 7).
Alle Texte sind eigene Formulierungen; Vorbild-Texte werden nicht übernommen (Notiz 07, Abschnitt 9 B).

Quellen: `research_notes/`.
>>>>>>> main
