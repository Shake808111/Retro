-- =====================================================================
-- V1_3__nick_skins.sql - Nick-Pool (Spec 01 §6.11): Skins getrennt von den Namen.
-- core.nick_skin hält Mojang-signierte Texturen (Quelle Mojang-Sessionserver mit Quell-UUID oder MineSkin mit
-- Skin-ID). /nickpool fill erzeugt Namen und kombiniert jeden zufällig mit einem Skin; die Textur wird in
-- core.nick_pool kopiert (dort NOT NULL, NickService liest nur nick_pool), skin_id verweist auf die Herkunft.
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

CREATE TABLE core.nick_skin (
  id                BIGSERIAL    PRIMARY KEY,
  source            VARCHAR(16)  NOT NULL CHECK (source IN ('MOJANG', 'MINESKIN', 'MANUAL')),
  source_ref        VARCHAR(64)  NOT NULL,
  texture_value     TEXT         NOT NULL,
  texture_signature TEXT         NOT NULL,
  enabled           BOOLEAN      NOT NULL DEFAULT true,
  fetched_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (source, source_ref)
);

ALTER TABLE core.nick_pool
  ADD COLUMN skin_id    BIGINT      NULL REFERENCES core.nick_skin (id) ON DELETE SET NULL,
  ADD COLUMN source     VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('GENERATED', 'MANUAL')),
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Namen sind wie Spielernamen case-insensitiv eindeutig (nick:name:{lower}).
CREATE UNIQUE INDEX nick_pool_name_lower ON core.nick_pool (lower(name));

-- Füllvorgang: erzeugte Namen dürfen keinem bekannten Spieler gehören, auch nicht früheren Namen.
CREATE INDEX player_name_history_lower ON core.player_name_history (lower(name));
