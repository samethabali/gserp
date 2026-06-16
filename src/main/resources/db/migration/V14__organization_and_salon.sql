-- V14: Multi-tenant master — organization + salon

CREATE TABLE organization (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'STANDALONE'
                CHECK (type IN ('STANDALONE', 'FRANCHISE')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE salon (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT       NOT NULL REFERENCES organization(id),
    slug             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'Europe/Istanbul',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_salon_org ON salon(organization_id);
CREATE INDEX idx_salon_slug ON salon(slug);

INSERT INTO organization (id, name, type, active, created_at)
VALUES (1, 'GSERP Default', 'STANDALONE', TRUE, NOW());

INSERT INTO salon (id, organization_id, slug, name, timezone, active, created_at)
VALUES (1, 1, 'default', 'GSERP Salon', 'Europe/Istanbul', TRUE, NOW());

SELECT setval('organization_id_seq', (SELECT MAX(id) FROM organization));
SELECT setval('salon_id_seq', (SELECT MAX(id) FROM salon));
