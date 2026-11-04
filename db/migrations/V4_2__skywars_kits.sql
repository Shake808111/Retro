-- =====================================================================
-- V4_2__skywars_kits.sql - Kit-Besitz und Kit-Auswahl (Spec 05 §2.1, für SkyWars Spec 04 §2.1/§5.9 vorgezogen)
-- und der Modus skywars_classic im Statistik-Katalog (Spec 04 §6.2.7).
-- games.kit_owned/kit_selected gehören laut Spec 05 zu V5_1__klassiker; V5_1 legt sie daher nicht erneut an.
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS games;

-- Gekaufte Kits je Modus (SkyWars, EnderGames …); Kauf über economy.ledger mit idem_key kit:{mode}:{kit}:{uuid}
CREATE TABLE IF NOT EXISTS games.kit_owned (
  uuid      UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  mode      VARCHAR(24) NOT NULL,
  kit_id    VARCHAR(32) NOT NULL,
  price     BIGINT      NOT NULL CHECK (price >= 0),
  bought_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (uuid, mode, kit_id)
);
CREATE INDEX IF NOT EXISTS kit_owned_mode_kit_idx ON games.kit_owned (mode, kit_id);

-- Zuletzt gewähltes Kit je Modus (random erlaubt), vorbelegt in der Wartelobby
CREATE TABLE IF NOT EXISTS games.kit_selected (
  uuid   UUID        NOT NULL REFERENCES core.player (uuid) ON DELETE CASCADE,
  mode   VARCHAR(24) NOT NULL,
  kit_id VARCHAR(32) NOT NULL,
  PRIMARY KEY (uuid, mode)
);

-- Classic SkyWars als eigener Statistik-Modus (gleiche Punkteformel wie SkyWars, Spec 02 §6.7)
INSERT INTO stats.mode (id, key, i18n_key, color, gradient, ranked, metric, points_formula, fields, resettable,
                        icon)
VALUES (16, 'skywars_classic', 'mode.skywars_classic', 'aqua', NULL, true, 'points', '{"kills":1,"wins":5}',
        '["rank","points","kills","deaths","kd","games","wins","winrate"]', true, 'GRASS_BLOCK');
