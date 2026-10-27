-- =====================================================================
-- V8_1__infra.sql - Infrastruktur & Dienste (Spec 08 §2.1)
-- Instanz-Historie, Map-Registry mit Versionen, Admin-Audit, Discord-Erweiterungen, API-Tokens.
-- Aufbewahrung (Controller-Job, Spec 08 §6.10): server_instance 90 Tage nach stopped_at,
-- admin_action 365 Tage; map_version mit active=false bleibt.
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS infra;

CREATE TABLE infra.server_instance (           -- Historie jeder gestarteten Instanz
  id            UUID PRIMARY KEY,
  name          VARCHAR(48)  NOT NULL,          -- z. B. lobby-3, bedwars-4x4-12
  template      VARCHAR(32)  NOT NULL,
  category      VARCHAR(12)  NOT NULL,          -- LOBBY | GAME | PERSISTENT
  launcher      VARCHAR(12)  NOT NULL,          -- STATIC | DOCKER | AGONES
  host          VARCHAR(64)  NOT NULL,
  port          INT          NOT NULL CHECK (port BETWEEN 1 AND 65535),
  build         VARCHAR(48),                    -- BuildInfo.VERSION + Git-SHA
  map_id        VARCHAR(32),
  map_version   INT,
  started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ready_at      TIMESTAMPTZ,
  stopped_at    TIMESTAMPTZ,
  stop_reason   VARCHAR(24),                    -- MATCH_END|IDLE|DRAIN|ADMIN|BOOT_TIMEOUT|HEARTBEAT_LOST|CRASH|MAX_LIFETIME|RESTART
  matches       INT          NOT NULL DEFAULT 0,
  peak_online   SMALLINT     NOT NULL DEFAULT 0);
CREATE INDEX server_instance_tpl_idx  ON infra.server_instance (template, started_at DESC);
CREATE UNIQUE INDEX server_instance_live_name ON infra.server_instance (name) WHERE stopped_at IS NULL;

CREATE TABLE infra.map (
  id            VARCHAR(32)  PRIMARY KEY CHECK (id ~ '^[a-z0-9_]{2,32}$'),
  mode          VARCHAR(24)  NOT NULL,
  display_name  VARCHAR(24)  NOT NULL,
  variants      TEXT[]       NOT NULL DEFAULT '{}',
  builders      TEXT[]       NOT NULL DEFAULT '{}',
  min_players   SMALLINT     NOT NULL,
  max_players   SMALLINT     NOT NULL CHECK (max_players >= min_players),
  enabled       BOOLEAN      NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE INDEX map_mode_idx ON infra.map (mode) WHERE enabled;

CREATE TABLE infra.map_version (
  map_id        VARCHAR(32)  NOT NULL REFERENCES infra.map ON DELETE CASCADE,
  version       INT          NOT NULL CHECK (version >= 1),
  sha256        CHAR(64)     NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  storage_key   TEXT         NOT NULL,          -- maps/{mode}/{id}/{version}.zip
  meta          JSONB        NOT NULL,          -- vollständige map.json
  uploaded_by   VARCHAR(64)  NOT NULL,          -- Git-Autor oder Staff-Name
  uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  active        BOOLEAN      NOT NULL DEFAULT false,
  PRIMARY KEY (map_id, version));
CREATE UNIQUE INDEX map_version_one_active ON infra.map_version (map_id) WHERE active;

CREATE TABLE infra.admin_action (               -- Audit für /cloud, /send, Map-Schalter
  id            BIGSERIAL    PRIMARY KEY,
  actor         UUID,                           -- NULL = System/CLI
  action        VARCHAR(24)  NOT NULL,          -- START|STOP|DRAIN|RESTART|SCALE|RELOAD|MAP_ENABLE|MAP_DISABLE|SEND
  target        VARCHAR(64)  NOT NULL,
  args          JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE INDEX admin_action_time_idx ON infra.admin_action (created_at DESC);

-- Discord (Tabelle integ.discord_link existiert aus V1__init: uuid PK, discord_id BIGINT UNIQUE, linked_at)
ALTER TABLE integ.discord_link
  ADD COLUMN nick_sync       BOOLEAN     NOT NULL DEFAULT true,
  ADD COLUMN roles_synced_at TIMESTAMPTZ;
CREATE TABLE integ.discord_role_map (
  lp_group        VARCHAR(32) PRIMARY KEY,      -- LuckPerms-Gruppe (Spec 01 §6.1)
  discord_role_id BIGINT      NOT NULL UNIQUE);

CREATE TABLE integ.api_token (                  -- interne/Staff-Zugriffe auf services:api
  id            BIGSERIAL    PRIMARY KEY,
  name          VARCHAR(48)  NOT NULL,
  token_sha256  BYTEA        NOT NULL UNIQUE,   -- nur Hash speichern
  scopes        TEXT[]       NOT NULL,          -- z. B. {status:read, chatlog:read, appeal:write}
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_used_at  TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ);
