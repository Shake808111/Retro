-- =====================================================================
-- V1_1__proxy_social.sql - Proxy & Social (Spec 01 §2.2)
-- Freunde-Favoriten, Ignorieren, Privatsphäre/Einstellungen, Clan-Erweiterungen
-- (Einladungen, Anfragen, Statistik-Modi, Elo je Modus, Clan-Wars, Umbenennungen),
-- Strafen (Anzeige-ID, Herkunft, Stufen, Gründe, IP-Bann), Reports, Nick-Pool,
-- Wartungs-Whitelist und Netzwerk-Einstellungen.
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

-- Defaults nachziehen
ALTER TABLE social.friend_request ALTER COLUMN created_at SET DEFAULT now();
CREATE INDEX ON social.friend_request (receiver);
CREATE INDEX ON social.friendship (b);

-- Privatsphäre/Einstellungen (Werte siehe Spec 01 §6.13)
ALTER TABLE social.privacy
  ADD COLUMN friend_req_from  SMALLINT NOT NULL DEFAULT 2,  -- 0 niemand, 1 ab Premium, 2 alle
  ADD COLUMN jump_from        SMALLINT NOT NULL DEFAULT 1,  -- 0 niemand, 1 Freunde, 2 nur Favoriten
  ADD COLUMN notify_friends   BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN notify_clan      BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN hide_from_clan   BOOLEAN  NOT NULL DEFAULT false, -- ab Premium
  ADD COLUMN global_chat      BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN nick_on_join     BOOLEAN  NOT NULL DEFAULT false,
  ADD COLUMN nick_keep        BOOLEAN  NOT NULL DEFAULT false,
  ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT now();
-- vorhandene Spalten: friend_requests (Master-Schalter, bleibt), msg_from 0/1/2, party_from 0/1/2, show_online
-- Standard Partyeinladungen laut Spec 01 §6.13: 2 = alle (V1 hatte 1).
ALTER TABLE social.privacy ALTER COLUMN party_from SET DEFAULT 2;

CREATE TABLE social.friend_favorite (            -- pro Richtung (owner markiert friend)
  owner        UUID NOT NULL,
  friend       UUID NOT NULL,
  notify       BOOLEAN NOT NULL DEFAULT true,    -- Online-Meldung trotz notify_friends=false
  allow_jump   BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner, friend)
);

CREATE TABLE social.ignore (
  owner      UUID NOT NULL,
  target     UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner, target)
);
CREATE INDEX ON social.ignore (target);

COMMENT ON COLUMN social.clan.elo IS 'deprecated: Elo je Modus in social.clan_elo (Spec 01 §2.1)';
ALTER TABLE social.clan
  ADD COLUMN join_mode     SMALLINT NOT NULL DEFAULT 1,     -- 0 nur Einladung, 1 Einladung+Anfrage
  ADD COLUMN show_roles    BOOLEAN  NOT NULL DEFAULT false, -- Leader: Rangunterschiede sichtbar
  ADD COLUMN xp            BIGINT   NOT NULL DEFAULT 0,
  ADD COLUMN last_active   TIMESTAMPTZ NOT NULL DEFAULT now(), -- letzter Login eines Mitglieds
  ADD COLUMN description   VARCHAR(64);
CREATE INDEX ON social.clan (last_active);
ALTER TABLE social.clan_member
  ADD CONSTRAINT clan_member_role_chk CHECK (role IN (-1,0,1,2)); -- -1 Testmitglied, 0 Mitglied, 1 Moderator, 2 Leiter
CREATE UNIQUE INDEX clan_one_leader ON social.clan_member (clan_id) WHERE role = 2;

CREATE TABLE social.clan_invite (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  uuid UUID, invited_by UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, uuid));
CREATE TABLE social.clan_request (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  uuid UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, uuid));
CREATE TABLE social.clan_stats_mode (            -- /clan togglestats
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  mode VARCHAR(24), enabled BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (clan_id, mode));
CREATE TABLE social.clan_elo (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  mode VARCHAR(24), elo INT NOT NULL DEFAULT 1000,
  games INT NOT NULL DEFAULT 0, wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, mode));
CREATE INDEX ON social.clan_elo (mode, elo DESC);
CREATE TABLE social.clan_war (
  id BIGSERIAL PRIMARY KEY, mode VARCHAR(24) NOT NULL, ranked BOOLEAN NOT NULL,
  clan_a BIGINT, clan_b BIGINT, winner BIGINT, elo_delta INT NOT NULL DEFAULT 0,
  match_id UUID, hidden BOOLEAN NOT NULL DEFAULT false,      -- true nach /clanstatsreset
  started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ);
CREATE INDEX ON social.clan_war (clan_a, ended_at DESC);
CREATE INDEX ON social.clan_war (clan_b, ended_at DESC);
CREATE TABLE social.clan_rename_log (clan_id BIGINT, at TIMESTAMPTZ NOT NULL DEFAULT now(),
  old_name VARCHAR(16), old_tag VARCHAR(5), PRIMARY KEY (clan_id, at));

-- Strafen
ALTER TABLE moderation.punishment
  ADD COLUMN public_id VARCHAR(16),               -- Anzeige-ID, Spec 01 §6.10
  ADD COLUMN origin    VARCHAR(8) NOT NULL DEFAULT 'MANUAL', -- MANUAL, REPORT, AC, AUTO
  ADD COLUMN ac_code   INT,                       -- Anticheat-Code "(3574)"
  ADD COLUMN stage     SMALLINT NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX ON moderation.punishment (public_id);
CREATE INDEX ON moderation.punishment (uuid, type, issued_at DESC);
CREATE TABLE moderation.reason (
  code VARCHAR(32) PRIMARY KEY, type VARCHAR(8) NOT NULL,       -- BAN/MUTE
  label_key VARCHAR(64) NOT NULL,                               -- i18n-Schlüssel
  stages_s BIGINT[] NOT NULL,                                   -- Dauer je Stufe, -1 = permanent
  reportable BOOLEAN NOT NULL DEFAULT false, report_scope VARCHAR(32)); -- z. B. 'TTT,REPLAY'
CREATE TABLE moderation.ban_ip (ip_hmac BYTEA, punishment_id BIGINT REFERENCES moderation.punishment,
  PRIMARY KEY (ip_hmac, punishment_id));
ALTER TABLE moderation.report ADD COLUMN public_id VARCHAR(12);
CREATE INDEX ON moderation.report (status, created_at);
CREATE INDEX ON moderation.report (target, created_at DESC);

-- Nick
CREATE TABLE core.nick_pool (
  name VARCHAR(16) PRIMARY KEY, texture_value TEXT NOT NULL, texture_signature TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true);

-- Wartung
CREATE TABLE core.maintenance_whitelist (uuid UUID PRIMARY KEY, added_by UUID,
  added_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE core.network_setting (key VARCHAR(64) PRIMARY KEY, value JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()); -- maintenance, motd, tablist (Hot-Reload)
