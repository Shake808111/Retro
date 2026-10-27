-- =====================================================================
-- V2_1__core_ui_stats.sql - Kern-UI, Daten & Stats (Spec 02 §2.1)
-- Tagesbuckets statt stats.total/stats.monthly (beim Release leer, werden gelöscht),
-- Modus-Katalog mit Seed (§6.7), Gesamt- und Map-Werte, Reset-Log, globales Level,
-- Gold und ResetTokens in economy, Anker für Bestenlisten-Wände und Hologramme.
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

DROP TABLE stats.total;
DROP TABLE stats.monthly;

-- Hilfsfunktion: addiert numerische Schlüssel zweier JSON-Objekte (übrige Schlüssel: Wert aus b, sonst a).
CREATE FUNCTION stats.jsonb_add(a JSONB, b JSONB) RETURNS JSONB
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT COALESCE(jsonb_object_agg(k,
           CASE WHEN jsonb_typeof(a -> k) = 'number' AND jsonb_typeof(b -> k) = 'number'
                THEN to_jsonb((a ->> k)::numeric + (b ->> k)::numeric)
                ELSE COALESCE(b -> k, a -> k) END), '{}'::jsonb)
  FROM (SELECT jsonb_object_keys(COALESCE(a, '{}'::jsonb))
        UNION
        SELECT jsonb_object_keys(COALESCE(b, '{}'::jsonb))) AS keys(k)
$$;

-- Modus-Katalog (Seed unten)
CREATE TABLE stats.mode (
  id              SMALLINT    PRIMARY KEY,
  key             VARCHAR(24) NOT NULL UNIQUE,
  i18n_key        VARCHAR(48) NOT NULL,
  color           VARCHAR(16) NOT NULL,           -- MiniMessage-Farbe
  gradient        VARCHAR(32),                    -- #RRGGBB:#RRGGBB
  ranked          BOOLEAN     NOT NULL DEFAULT true,
  metric          VARCHAR(12) NOT NULL DEFAULT 'points', -- points / karma / time / none
  points_formula  JSONB       NOT NULL DEFAULT '{}',
  fields          JSONB       NOT NULL,           -- geordnete Stat-Keys für /stats
  resettable      BOOLEAN     NOT NULL DEFAULT false,
  active          BOOLEAN     NOT NULL DEFAULT true
);

-- Tagesbuckets (Europe/Berlin), eine Partition pro Monat, Aufbewahrung 35 Tage
CREATE TABLE stats.daily (
  uuid     UUID     NOT NULL,
  mode_id  SMALLINT NOT NULL,
  day      DATE     NOT NULL,
  kills    INT      NOT NULL DEFAULT 0,
  deaths   INT      NOT NULL DEFAULT 0,
  games    INT      NOT NULL DEFAULT 0,
  wins     INT      NOT NULL DEFAULT 0,
  points   BIGINT   NOT NULL DEFAULT 0,
  extra    JSONB    NOT NULL DEFAULT '{}',
  PRIMARY KEY (uuid, mode_id, day)
) PARTITION BY RANGE (day);
CREATE INDEX ON stats.daily (mode_id, day);

-- Legt die Monatspartition stats.daily_YYYYMM an (idempotent). Genutzt von dieser Migration und vom
-- Partitionsjob (am 25. für Monat +1, Spec 02 §2.1).
CREATE FUNCTION stats.create_daily_partition(p_month DATE) RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
  v_start DATE := date_trunc('month', p_month)::date;
  v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
  v_name  TEXT := 'daily_' || to_char(v_start, 'YYYYMM');
BEGIN
  EXECUTE format('CREATE TABLE IF NOT EXISTS stats.%I PARTITION OF stats.daily FOR VALUES FROM (%L) TO (%L)',
                 v_name, v_start, v_end);
  RETURN 'stats.' || v_name;
END
$$;
SELECT stats.create_daily_partition((now() AT TIME ZONE 'Europe/Berlin')::date);
SELECT stats.create_daily_partition(((now() AT TIME ZONE 'Europe/Berlin') + INTERVAL '1 month')::date);

-- Gesamtwerte, inkrementell
CREATE TABLE stats.alltime (
  uuid           UUID     NOT NULL,
  mode_id        SMALLINT NOT NULL,
  kills          BIGINT   NOT NULL DEFAULT 0,
  deaths         BIGINT   NOT NULL DEFAULT 0,
  games          BIGINT   NOT NULL DEFAULT 0,
  wins           BIGINT   NOT NULL DEFAULT 0,
  points         BIGINT   NOT NULL DEFAULT 0,
  extra          JSONB    NOT NULL DEFAULT '{}',
  first_game_at  TIMESTAMPTZ,
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (uuid, mode_id)
);
CREATE INDEX ON stats.alltime (mode_id, points DESC, wins DESC, uuid);

-- Stats pro Map (SkyWars-Map-Menü)
CREATE TABLE stats.map_alltime (
  uuid     UUID        NOT NULL,
  mode_id  SMALLINT    NOT NULL,
  map      VARCHAR(48) NOT NULL,
  kills    INT         NOT NULL DEFAULT 0,
  deaths   INT         NOT NULL DEFAULT 0,
  games    INT         NOT NULL DEFAULT 0,
  wins     INT         NOT NULL DEFAULT 0,
  points   BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, mode_id, map)
);

-- Idempotenz der Runde und Audit (Rundenblock)
ALTER TABLE stats.match
  ADD COLUMN mode_id    SMALLINT,
  ADD COLUMN applied_at TIMESTAMPTZ;
ALTER TABLE stats.match_player
  ADD COLUMN wins   SMALLINT NOT NULL DEFAULT 0,
  ADD COLUMN points INT      NOT NULL DEFAULT 0,
  ADD COLUMN xp     INT      NOT NULL DEFAULT 0;

-- /togglestats
CREATE TABLE stats.player_settings (
  uuid          UUID PRIMARY KEY,
  hidden        BOOLEAN NOT NULL DEFAULT false,
  hidden_since  TIMESTAMPTZ
);

-- Globales Level (NetworkLevelService, Kurve xpFür(n) = 250·n·(n+1))
CREATE TABLE stats.network_level (
  uuid        UUID PRIMARY KEY,
  xp          BIGINT NOT NULL DEFAULT 0,
  level       INT    NOT NULL DEFAULT 0,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON stats.network_level (level DESC, xp DESC);

-- Snapshot vor dem Löschen (Statsreset)
CREATE TABLE stats.reset_log (
  id          BIGSERIAL PRIMARY KEY,
  uuid        UUID       NOT NULL,
  mode_id     SMALLINT   NOT NULL,
  scope       VARCHAR(8) NOT NULL CHECK (scope IN ('player', 'staff', 'clan')),
  ledger_id   BIGINT,
  actor       UUID,
  snapshot    JSONB      NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON stats.reset_log (uuid, created_at DESC);

-- Gold (Scoreboard) und ResetTokens
ALTER TABLE economy.wallet
  ADD COLUMN gold         BIGINT NOT NULL DEFAULT 0 CHECK (gold >= 0),
  ADD COLUMN reset_tokens INT    NOT NULL DEFAULT 0 CHECK (reset_tokens >= 0);
ALTER TABLE economy.ledger
  ADD COLUMN currency VARCHAR(12) NOT NULL DEFAULT 'COINS' CHECK (currency IN ('COINS', 'GOLD', 'RESET_TOKEN'));
CREATE INDEX ON economy.ledger (uuid, created_at DESC);

-- Positionen von Bestenlisten-Wänden und Hologrammen
CREATE TABLE core.display_anchor (
  id            BIGSERIAL PRIMARY KEY,
  server_group  VARCHAR(32) NOT NULL,
  world         VARCHAR(64) NOT NULL,
  type          VARCHAR(16) NOT NULL CHECK (type IN ('SIGN_WALL', 'HOLO_TOP', 'HOLO_PERSONAL')),
  mode_id       SMALLINT    NOT NULL,
  period        VARCHAR(4)  NOT NULL CHECK (period IN ('30d', 'all')),
  size          SMALLINT    NOT NULL DEFAULT 10,
  x             DOUBLE PRECISION,
  y             DOUBLE PRECISION,
  z             DOUBLE PRECISION,
  yaw           REAL,
  facing        VARCHAR(8),
  created_by    UUID,
  created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON core.display_anchor (server_group);

-- Seed Modus-Katalog (Spec 02 §6.7). Farben = modeColor der Modus-Präfixe (Spec 01 §4.2, Spec 05/06 §7),
-- Verlauf JumpLeague (Spec 02 O18). metric 'none' = kein Ranking (ranked = false).
-- Punkte-Formeln als Gewichte je Zähler; leer = Punkte liefert das Modul (§6.4 Nr. 2).
INSERT INTO stats.mode (id, key, i18n_key, color, gradient, ranked, metric, points_formula, fields, resettable)
VALUES
  (1,  'bedwars',        'mode.bedwars',        'red',          NULL,              true,  'points',
       '{"kills":5,"beds":10,"wins":50}',
       '["rank","points","kills","deaths","kd","games","wins","winrate","beds"]', true),
  (2,  'skywars',        'mode.skywars',        'aqua',         NULL,              true,  'points',
       '{"kills":1,"wins":5}',
       '["rank","points","kills","deaths","kd","games","wins","winrate"]', true),
  (3,  'jumpleague',     'mode.jumpleague',     'red',          '#D95D51:#ECE12A', true,  'points',
       '{"goals_clean":40,"wins":50,"kills":10}',
       '["rank","points","kills","kd","wins","goals_clean"]', true),
  (4,  'ttt',            'mode.ttt',            'red',          NULL,              true,  'karma',
       '{}',
       '["rank","karma","kills","deaths","games","wins"]', true),
  (5,  'cores',          'mode.cores',          'aqua',         NULL,              true,  'points',
       '{"kills":5,"cores":20,"wins":50}',
       '["rank","points","kills","deaths","kd","games","wins","winrate","cores"]', false),
  (6,  'partygames',     'mode.partygames',     'light_purple', NULL,              true,  'points',
       '{}',
       '["rank","points","games","wins","winrate","minigames_played","minigames_won"]', false),
  (7,  'survivalgames',  'mode.survivalgames',  'aqua',         NULL,              true,  'points',
       '{"kills":1,"wins":5}',
       '["rank","points","kills","deaths","kd","games","wins","winrate"]', false),
  (8,  'endergames',     'mode.endergames',     'dark_purple',  NULL,              true,  'points',
       '{"kills":1,"wins":5}',
       '["rank","points","kills","deaths","kd","games","wins","winrate"]', false),
  (9,  'gungame',        'mode.gungame',        'gold',         NULL,              true,  'points',
       '{"kills":1}',
       '["rank","points","kills","deaths","kd","level_record"]', true),
  (10, 'masterbuilders', 'mode.masterbuilders', 'blue',         NULL,              false, 'none',
       '{}',
       '["games","wins","losses","points"]', false),
  (11, 'bingo_x1',       'mode.bingo_x1',       'red',          NULL,              true,  'time',
       '{}',
       '["rank","best_time","games","wins"]', false),
  (12, 'bingo_x2',       'mode.bingo_x2',       'red',          NULL,              true,  'time',
       '{}',
       '["rank","best_time","games","wins"]', false),
  (13, 'bingo_x3',       'mode.bingo_x3',       'red',          NULL,              true,  'time',
       '{}',
       '["rank","best_time","games","wins"]', false),
  (14, 'duels',          'mode.duels',          'blue',         NULL,              false, 'none',
       '{}',
       '[]', true);
