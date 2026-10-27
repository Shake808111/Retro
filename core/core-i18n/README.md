# core/core-i18n – Texte und Sprachen

**Gradle-Pfad:** `:core:core-i18n` · **Paket:** `net.retromc.core.i18n` · **Einstieg:** `Messages` · **Status:** Gerüst

## Verantwortung

Alle Spielertexte im MiniMessage-Format, **Deutsch zuerst** (`messages.properties` = de_DE, Rückfallsprache),
Englisch in `messages_en.properties` (Notiz 07, Abschnitt 5 „Text and i18n“).
Der Platzhalter `<brand>` wird durch den Markennamen aus `gradle.properties` ersetzt. Das Rendern zu
Adventure-Komponenten übernehmen `paper-core` und `velocity-core` (MiniMessage ist dort eingebaut).

## Welches Vorbild-Feature wird nachgebaut?

Die Sprachwahl Deutsch/Englisch, beim Vorbild über `/language` (Notiz 05, Abschnitt 7).
Alle Texte sind eigene Formulierungen; Vorbild-Texte werden nicht übernommen (Notiz 07, Abschnitt 9 B).

Quellen: `research_notes/`.
