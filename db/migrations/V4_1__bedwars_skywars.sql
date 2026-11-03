-- =====================================================================
-- V4_1__bedwars_skywars.sql - BedWars und SkyWars (Spec 04 §2.1, Schema games; K2, K5 der Integration).
-- Schnellkauf, Shop-Einstellungen, Gold-Vote (Classic), Modus-Level, SkyWars-Kit-Einstellungen/-Stufen und die
-- Ranked-Option (Standard aus). Alle uuid-Spalten verweisen auf core.player (ON DELETE CASCADE).
-- Dazu der Modus bedwars_classic im Statistik-Katalog (Spec 04 §6.1.9: Ranking nach Siegen).
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS games;

-- Schnellkauf-Belegung (21 Felder, 3x7-Raster)
CREATE TABLE games.bw_quickshop (
  uuid          UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  variant_group VARCHAR(16) NOT NULL DEFAULT 'modern' CHECK (variant_group IN ('modern', 'classic')),
  slot          SMALLINT    NOT NULL CHECK (slot BETWEEN 0 AND 20),
  item_id       VARCHAR(40) NOT NULL,
  PRIMARY KEY (uuid, variant_group, slot)
);

-- Shop-Einstellungen (roter Beutel): Kategorie-Reihenfolge und bevorzugte Hotbar-Slots
CREATE TABLE games.bw_shop_settings (
  uuid           UUID       PRIMARY KEY REFERENCES core.player (uuid) ON DELETE CASCADE,
  category_order SMALLINT[] NOT NULL DEFAULT '{0,1,2,3,4,5,6,7,8}',
  hotbar_pref    JSONB      NOT NULL DEFAULT '{}',
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Gespeicherte Gold-Wahl (Classic, /goldvote save)
CREATE TABLE games.bw_goldvote (
  uuid     UUID       PRIMARY KEY REFERENCES core.player (uuid) ON DELETE CASCADE,
  rate     VARCHAR(8) NOT NULL CHECK (rate IN ('none', 'low', 'normal', 'high')),
  saved_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Modus-Level (BedWars-XP, SkyWars-Level)
CREATE TABLE games.mode_level (
  uuid       UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  mode       VARCHAR(24) NOT NULL,
  xp         BIGINT      NOT NULL DEFAULT 0 CHECK (xp >= 0),
  level      INT         NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (uuid, mode)
);
CREATE INDEX ON games.mode_level (mode, level DESC, xp DESC);

-- SkyWars: Kit-Einstellungen (frühere Kits wieder aktivieren) und Kit-Stufen
CREATE TABLE games.sw_kit_settings (
  uuid           UUID          PRIMARY KEY REFERENCES core.player (uuid) ON DELETE CASCADE,
  legacy_enabled BOOLEAN       NOT NULL DEFAULT false,
  hidden_kits    VARCHAR(32)[] NOT NULL DEFAULT '{}'
);

CREATE TABLE games.sw_kit_uses (
  uuid   UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  kit_id VARCHAR(32) NOT NULL,
  tier   SMALLINT    NOT NULL DEFAULT 1,
  PRIMARY KEY (uuid, kit_id)
);

-- Ranked-Option (Standard aus)
CREATE TABLE games.ranked_rating (
  uuid       UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  mode       VARCHAR(24) NOT NULL,
  season     SMALLINT    NOT NULL,
  elo        INT         NOT NULL DEFAULT 0,
  league     VARCHAR(16) NOT NULL DEFAULT 'unranked',
  placements SMALLINT    NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (uuid, mode, season)
);
CREATE INDEX ON games.ranked_rating (mode, season, elo DESC);

-- Classic BedWars als eigener Statistik-Modus (Spec 04 §6.1.9: points_formula {"wins":1})
INSERT INTO stats.mode (id, key, i18n_key, color, gradient, ranked, metric, points_formula, fields, resettable,
                        icon)
VALUES (15, 'bedwars_classic', 'mode.bedwars_classic', 'red', NULL, true, 'points', '{"wins":1}',
        '["rank","points","kills","deaths","kd","games","wins","winrate","beds"]', true, 'RED_BED');
