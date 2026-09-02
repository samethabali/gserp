-- V25: davet kodu, tanıtım (showcase) tenant, müşteri/işlem hareket kaydı

ALTER TABLE salon ADD COLUMN IF NOT EXISTS showcase BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE organization SET name = 'GSCRM Default' WHERE id = 1 AND name = 'GSERP Default';
UPDATE salon SET name = 'GSCRM Salon' WHERE id = 1 AND name = 'GSERP Salon';

CREATE TABLE invite_code (
    id                         BIGSERIAL PRIMARY KEY,
    code                       VARCHAR(32)  NOT NULL UNIQUE,
    kind                       VARCHAR(16)  NOT NULL
                               CHECK (kind IN ('PILOT', 'SHOWCASE')),
    max_uses                   INT          NOT NULL DEFAULT 1,
    used_count                 INT          NOT NULL DEFAULT 0,
    expires_at                 TIMESTAMP,
    revoked_at                 TIMESTAMP,
    plan_code                  VARCHAR(32)  NOT NULL DEFAULT 'SOLO',
    organization_type          VARCHAR(16)  NOT NULL DEFAULT 'STANDALONE',
    note                       VARCHAR(255),
    created_by                 BIGINT       REFERENCES users(id),
    created_at                 TIMESTAMP    NOT NULL DEFAULT NOW(),
    redeemed_organization_id   BIGINT       REFERENCES organization(id)
);

CREATE INDEX idx_invite_code_kind ON invite_code (kind);

CREATE TABLE activity_event (
    id              BIGSERIAL PRIMARY KEY,
    salon_id        BIGINT       NOT NULL REFERENCES salon(id),
    customer_id     BIGINT       REFERENCES customer(id),
    actor_user_id   BIGINT,
    actor_username  VARCHAR(64),
    action          VARCHAR(32)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       BIGINT,
    summary         VARCHAR(512) NOT NULL,
    detail          TEXT,
    ip              VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_salon_customer ON activity_event (salon_id, customer_id, created_at DESC);
CREATE INDEX idx_activity_salon_created ON activity_event (salon_id, created_at DESC);
