-- =====================================================================
-- V1_2__seed_reasons.sql - Strafgründe und Stufen (Spec 01 §2.2 Seed, Tabelle §6.10, O17)
-- Dauer je Stufe in Sekunden, -1 = permanent. Stufe = frühere, nicht widerrufene Strafen
-- gleichen Codes + 1, gedeckelt auf die letzte Stufe. Werte sind in moderation.reason änderbar.
-- reportable/report_scope: über /report meldbar (Spec 01 §6.9, O18); randomkilling nur TTT/Replay.
-- label_key: i18n-Schlüssel punish.reason.<code> (Texte in core-i18n).
-- =====================================================================

INSERT INTO moderation.reason (code, type, label_key, stages_s, reportable, report_scope) VALUES
  -- Hacking: 30 d -> 90 d -> perm
  ('hacking',       'BAN',  'punish.reason.hacking',       '{2592000,7776000,-1}', true,  NULL),
  -- Unerlaubte Clientmodifikation: 30 d -> perm (+ ac_code)
  ('clientmod',     'BAN',  'punish.reason.clientmod',     '{2592000,-1}',         false, NULL),
  -- Bannumgehung: perm
  ('ban_evasion',   'BAN',  'punish.reason.ban_evasion',   '{-1}',                 false, NULL),
  -- Teaming: 1 d -> 7 d -> 30 d
  ('teaming',       'BAN',  'punish.reason.teaming',       '{86400,604800,2592000}', true, NULL),
  -- Randomkilling: 1 d -> 3 d -> 7 d
  ('randomkilling', 'BAN',  'punish.reason.randomkilling', '{86400,259200,604800}', true,  'TTT,REPLAY'),
  -- Bugusing: 7 d -> 30 d -> perm
  ('bugusing',      'BAN',  'punish.reason.bugusing',      '{604800,2592000,-1}',  true,  NULL),
  -- Unangemessener Name/Skin: perm (bis Änderung, manuell entbannen)
  ('name_skin',     'BAN',  'punish.reason.name_skin',     '{-1}',                 true,  NULL),
  -- Beleidigung: 7 d -> 30 d -> perm
  ('chat_insult',   'MUTE', 'punish.reason.chat_insult',   '{604800,2592000,-1}',  true,  NULL),
  -- Respektloses und provokantes Verhalten: 3 d -> 7 d -> 30 d
  ('chat_respect',  'MUTE', 'punish.reason.chat_respect',  '{259200,604800,2592000}', true, NULL),
  -- Spam: 1 d -> 3 d -> 7 d
  ('chat_spam',     'MUTE', 'punish.reason.chat_spam',     '{86400,259200,604800}', true,  NULL),
  -- Werbung: 14 d -> 30 d -> perm
  ('chat_ad',       'MUTE', 'punish.reason.chat_ad',       '{1209600,2592000,-1}', true,  NULL);
