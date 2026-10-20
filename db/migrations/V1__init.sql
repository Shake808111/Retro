-- =====================================================================
-- V1__init.sql - Grundschema des Netzwerks (PostgreSQL)
-- Quelle: research_notes/GommeHD Analyse und Nachbau/07_technik_stack_recht.md,
--         Abschnitt 4 "Schema sketch (PostgreSQL, build-ready starting point)".
-- Übernommen wie skizziert; ergänzt wurden nur die CREATE-SCHEMA-Anweisungen
-- (ein Schema pro fachlichem Bereich, Notiz 07 Abschnitt 4) und Kommentare.
-- Ausführung: Flyway (docker compose -> Dienst "migrate", oder
--             ./gradlew :services:controller:run --args=migrate).
-- Bereits ausgeführte Migrationen nie ändern - Änderungen als V2__..., V3__... anlegen.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS social;
CREATE SCHEMA IF NOT EXISTS stats;
CREATE SCHEMA IF NOT EXISTS economy;
CREATE SCHEMA IF NOT EXISTS cosmetics;
CREATE SCHEMA IF NOT EXISTS moderation;
CREATE SCHEMA IF NOT EXISTS store;
CREATE SCHEMA IF NOT EXISTS integ;
-- Ränge: LuckPerms legt seine Tabellen selbst an, im eigenen Schema "luckperms" (Notiz 07, Abschnitt 4).
-- Anzeige-Metadaten (Präfix, Farbe, Gewicht) als LuckPerms-Meta.
CREATE SCHEMA IF NOT EXISTS luckperms;

-- ---------------------------------------------------------------------
-- core
-- ---------------------------------------------------------------------
CREATE TABLE core.player (
    uuid        UUID PRIMARY KEY,
    name        VARCHAR(16) NOT NULL,
    name_lower  VARCHAR(16) NOT NULL,
    first_join  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_join   TIMESTAMPTZ,
    last_quit   TIMESTAMPTZ,
    playtime_s  BIGINT      NOT NULL DEFAULT 0,
    locale      VARCHAR(8)  NOT NULL DEFAULT 'de_DE',
    is_bedrock  BOOLEAN     NOT NULL DEFAULT false,
    settings    JSONB       NOT NULL DEFAULT '{}'::jsonb
);
-- wird bei Namensänderung aktualisiert
CREATE UNIQUE INDEX ON core.player (name_lower);

CREATE TABLE core.player_name_history (
    uuid     UUID,
    name     VARCHAR(16),
    seen_at  TIMESTAMPTZ,
    PRIMARY KEY (uuid, name)
);

-- ip_hmac = HMAC(secret, ip); Lösch-/Aufbewahrungsjob nötig (Notiz 07, Abschnitt 9 E)
CREATE TABLE core.player_ip (
    uuid        UUID,
    ip_hmac     BYTEA,
    first_seen  TIMESTAMPTZ,
    last_seen   TIMESTAMPTZ,
    PRIMARY KEY (uuid, ip_hmac)
);

-- ---------------------------------------------------------------------
-- social
-- ---------------------------------------------------------------------
-- a < b in PostgreSQL-Reihenfolge (byteweise); in Java: dev.netzwerk.core.api.social.FriendPair
CREATE TABLE social.friendship (
    a      UUID,
    b      UUID,
    since  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (a, b),
    CHECK (a < b)
);

CREATE TABLE social.friend_request (
    sender      UUID,
    receiver    UUID,
    created_at  TIMESTAMPTZ,
    PRIMARY KEY (sender, receiver)
);

CREATE TABLE social.privacy (
    uuid             UUID PRIMARY KEY,
    friend_requests  BOOLEAN  DEFAULT true,
    msg_from         SMALLINT DEFAULT 1, -- 0 niemand, 1 Freunde, 2 alle
    party_from       SMALLINT DEFAULT 1,
    show_online      BOOLEAN  DEFAULT true
);

CREATE TABLE social.clan (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(24) NOT NULL,
    tag         VARCHAR(5)  NOT NULL,
    owner       UUID        NOT NULL,
    elo         INT         NOT NULL DEFAULT 1000,
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX ON social.clan (lower(name));
CREATE UNIQUE INDEX ON social.clan (lower(tag));

CREATE TABLE social.clan_member (
    clan_id    BIGINT REFERENCES social.clan ON DELETE CASCADE,
    uuid       UUID UNIQUE,
    role       SMALLINT NOT NULL, -- 0 Mitglied, 1 Moderator, 2 Leiter
    joined_at  TIMESTAMPTZ,
    PRIMARY KEY (clan_id, uuid)
);

-- Partys liegen nur in Valkey: party:{id} HASH, party:{id}:members SET,
-- player:{uuid}:party STRING, TTL bei Inaktivität (siehe dev.netzwerk.core.data.ValkeyKeys).

-- ---------------------------------------------------------------------
-- stats
-- ---------------------------------------------------------------------
CREATE TABLE stats.match (
    id           UUID PRIMARY KEY,
    mode         VARCHAR(24),
    variant      VARCHAR(24),
    map          VARCHAR(48),
    server       VARCHAR(64),
    started_at   TIMESTAMPTZ,
    ended_at     TIMESTAMPTZ,
    winner_team  SMALLINT
);

CREATE TABLE stats.match_player (
    match_id   UUID REFERENCES stats.match,
    uuid       UUID,
    team       SMALLINT,
    placement  SMALLINT,
    kills      INT,
    deaths     INT,
    extra      JSONB,
    PRIMARY KEY (match_id, uuid)
);

-- wins, kills, deaths, beds, games, points ...
CREATE TABLE stats.total (
    uuid   UUID,
    mode   VARCHAR(24),
    key    VARCHAR(32),
    value  BIGINT NOT NULL,
    PRIMARY KEY (uuid, mode, key)
);

-- Monatsranglisten / Statistik-Reset
CREATE TABLE stats.monthly (
    uuid   UUID,
    mode   VARCHAR(24),
    key    VARCHAR(32),
    month  DATE,
    value  BIGINT,
    PRIMARY KEY (uuid, mode, key, month)
);

-- Bestenlisten: Valkey-ZSET lb:{mode}:{key}:{alltime|YYYY-MM}, nachts aus SQL neu aufgebaut.

-- ---------------------------------------------------------------------
-- economy (nur anhängendes Ledger; Kontostand als materialisierte Spalte in derselben Transaktion)
-- ---------------------------------------------------------------------
CREATE TABLE economy.wallet (
    uuid   UUID PRIMARY KEY,
    coins  BIGINT NOT NULL DEFAULT 0 CHECK (coins >= 0)
);

CREATE TABLE economy.ledger (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID        NOT NULL,
    delta       BIGINT      NOT NULL,
    reason      VARCHAR(32) NOT NULL,
    ref         TEXT,
    idem_key    TEXT UNIQUE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- ---------------------------------------------------------------------
-- cosmetics
-- ---------------------------------------------------------------------
CREATE TABLE cosmetics.item (
    id           VARCHAR(64) PRIMARY KEY,
    category     VARCHAR(24),
    rarity       SMALLINT,
    price_coins  INT,
    purchasable  BOOLEAN,
    data         JSONB
);

CREATE TABLE cosmetics.owned (
    uuid         UUID,
    item_id      VARCHAR(64) REFERENCES cosmetics.item,
    source       VARCHAR(16), -- COINS, STORE, RANK, EVENT
    acquired_at  TIMESTAMPTZ,
    PRIMARY KEY (uuid, item_id)
);

CREATE TABLE cosmetics.equipped (
    uuid      UUID,
    category  VARCHAR(24),
    item_id   VARCHAR(64),
    PRIMARY KEY (uuid, category)
);

-- ---------------------------------------------------------------------
-- moderation
-- ---------------------------------------------------------------------
CREATE TABLE moderation.punishment (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID        NOT NULL,
    type          VARCHAR(8)  NOT NULL, -- BAN, MUTE, KICK, WARN
    reason_code   VARCHAR(32) NOT NULL,
    reason_text   TEXT,
    issued_by     UUID,
    issued_at     TIMESTAMPTZ DEFAULT now(),
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    revoked_by    UUID,
    revoke_note   TEXT,
    evidence_ref  TEXT,
    server        VARCHAR(64)
);
CREATE INDEX ON moderation.punishment (uuid) WHERE revoked_at IS NULL;

CREATE TABLE moderation.report (
    id          BIGSERIAL PRIMARY KEY,
    reporter    UUID,
    target      UUID,
    reason      VARCHAR(32),
    server      VARCHAR(64),
    match_id    UUID,
    chatlog_id  UUID,
    status      VARCHAR(12) DEFAULT 'OPEN',
    handled_by  UUID,
    created_at  TIMESTAMPTZ DEFAULT now(),
    handled_at  TIMESTAMPTZ
);

-- Aufbewahrungsjob nötig (Notiz 07, Abschnitt 9 E: 30-90 Tage für Meldungen)
CREATE TABLE moderation.chatlog (
    id          UUID PRIMARY KEY,
    created_by  UUID,
    target      UUID,
    server      VARCHAR(64),
    messages    JSONB NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- ---------------------------------------------------------------------
-- store & Integrationen
-- ---------------------------------------------------------------------
CREATE TABLE store.purchase (
    tx_id         VARCHAR(64) PRIMARY KEY,
    uuid          UUID,
    package_id    VARCHAR(64),
    status        VARCHAR(16),
    amount_cents  INT,
    currency      CHAR(3),
    created_at    TIMESTAMPTZ,
    delivered_at  TIMESTAMPTZ
);

CREATE TABLE integ.discord_link (
    uuid        UUID PRIMARY KEY,
    discord_id  BIGINT UNIQUE NOT NULL,
    linked_at   TIMESTAMPTZ
);
