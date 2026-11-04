-- =====================================================================
-- V2_3__npc.sql - dauerhafte NPCs aus /npc create (Spec 02 §8.1 NpcService, Admin-Befehl /npc).
-- Je Servergruppe (Template, z. B. lobby) die NPCs mit Ort, Name (MiniMessage) und Skin (textures-Property mit
-- Signatur, damit der Skin ohne den Quellspieler wieder geladen werden kann).
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

CREATE TABLE core.npc (
  id             BIGSERIAL        PRIMARY KEY,
  server_group   VARCHAR(32)      NOT NULL,
  world          VARCHAR(64)      NOT NULL,
  x              DOUBLE PRECISION NOT NULL,
  y              DOUBLE PRECISION NOT NULL,
  z              DOUBLE PRECISION NOT NULL,
  yaw            REAL             NOT NULL DEFAULT 0,
  pitch          REAL             NOT NULL DEFAULT 0,
  name           TEXT,
  skin_name      VARCHAR(16),
  skin_value     TEXT,
  skin_signature TEXT,
  look_at        BOOLEAN          NOT NULL DEFAULT true,
  created_by     UUID,
  created_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX ON core.npc (server_group);
