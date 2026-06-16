-- V23: Subscription and billing

CREATE TABLE subscription_plan (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    max_salons      INTEGER      NOT NULL DEFAULT 1,
    max_users       INTEGER      NOT NULL DEFAULT 5,
    whatsapp_quota  INTEGER      NOT NULL DEFAULT 500,
    price_monthly   NUMERIC(10,2) NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO subscription_plan (code, name, max_salons, max_users, whatsapp_quota, price_monthly) VALUES
    ('SOLO', 'Solo Salon', 1, 5, 500, 990.00),
    ('FRANCHISE_STARTER', 'Franchise Starter', 5, 20, 2000, 2490.00),
    ('FRANCHISE_PRO', 'Franchise Pro', 999, 999, 99999, 0.00);

CREATE TABLE organization_subscription (
    id                   BIGSERIAL PRIMARY KEY,
    organization_id      BIGINT       NOT NULL UNIQUE REFERENCES organization(id),
    plan_id              BIGINT       NOT NULL REFERENCES subscription_plan(id),
    status               VARCHAR(16)  NOT NULL DEFAULT 'TRIAL'
                         CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    trial_end            TIMESTAMP,
    current_period_end   TIMESTAMP,
    external_id          VARCHAR(128),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP
);

CREATE TABLE usage_meter (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organization(id),
    salon_id        BIGINT      REFERENCES salon(id),
    metric          VARCHAR(32) NOT NULL,
    period          VARCHAR(7)  NOT NULL,
    count           INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_usage_meter UNIQUE (organization_id, salon_id, metric, period)
);

CREATE TABLE billing_event (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organization(id),
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Default org on SOLO trial
INSERT INTO organization_subscription (organization_id, plan_id, status, trial_end)
SELECT 1, (SELECT id FROM subscription_plan WHERE code = 'SOLO'), 'ACTIVE', NOW() + INTERVAL '365 days';
