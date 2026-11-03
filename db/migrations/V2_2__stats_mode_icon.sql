-- =====================================================================
-- V2_2__stats_mode_icon.sql - Modus-Icon für das Statistik-Profil (Spec 02 §5.4: "Modus-Icon (stats.mode-Seed
-- icon)"; V2_1 hatte die Spalte nicht). Wert = Bukkit-Material (Annahme, Original-Layout unbelegt, Spec 02 O3).
-- Ausgeführte Migrationen nie ändern - Änderungen als neue Version anlegen.
-- =====================================================================

ALTER TABLE stats.mode ADD COLUMN icon VARCHAR(32) NOT NULL DEFAULT 'PAPER';

UPDATE stats.mode SET icon = v.icon
FROM (VALUES
  ('bedwars',        'RED_BED'),
  ('skywars',        'GRASS_BLOCK'),
  ('jumpleague',     'GOLDEN_BOOTS'),
  ('ttt',            'STICK'),
  ('cores',          'BEACON'),
  ('partygames',     'CAKE'),
  ('survivalgames',  'IRON_SWORD'),
  ('endergames',     'ENDER_PEARL'),
  ('gungame',        'WOODEN_AXE'),
  ('masterbuilders', 'BRICKS'),
  ('bingo_x1',       'FILLED_MAP'),
  ('bingo_x2',       'FILLED_MAP'),
  ('bingo_x3',       'FILLED_MAP'),
  ('duels',          'DIAMOND_SWORD')
) AS v(key, icon)
WHERE stats.mode.key = v.key;
