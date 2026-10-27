# core/core-messaging – netzwerkweite Nachrichten

**Gradle-Pfad:** `:core:core-messaging` · **Paket:** `net.retromc.core.messaging` · **Einstieg:** `MessageBus`, `ValkeyMessageBus`, `NetworkChannel` · **Status:** Gerüst

## Verantwortung

Nachrichten zwischen Velocity, Paper-Servern und Diensten über Valkey Pub/Sub (Notiz 07, Abschnitt 4).
Kanäle laut Notiz: `net:chat`, `net:social`, `net:moderation`, `net:queue`, `net:server-state`.
Für Ereignisse, die nicht verloren gehen dürfen, sieht die Notiz Valkey Streams vor; NATS JetStream ist die Alternative.

## Welches Vorbild-Feature wird nachgebaut?

Die serverübergreifenden Funktionen, die beim Vorbild proxyweit arbeiten: Freunde, Party, Clan, private Nachrichten
(Notiz 05, Abschnitte 3 und 7, z. B. `/friend`, `/party`, `/msg`), außerdem Meldungen an das Team (`/report`, Notiz 06, Abschnitt 2).

Quellen: `research_notes/`.
