-- V18: Branch overrides — service price, holidays, WhatsApp config

CREATE TABLE branch_service_price (
    id                BIGSERIAL PRIMARY KEY,
    salon_id          BIGINT         NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    service_id        BIGINT         NOT NULL REFERENCES service_definition(id) ON DELETE CASCADE,
    price_override    NUMERIC(12,2),
    duration_override INTEGER,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_branch_service_price UNIQUE (salon_id, service_id)
);

CREATE TABLE branch_holiday (
    id        BIGSERIAL PRIMARY KEY,
    salon_id  BIGINT  NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    reason    VARCHAR(255),
    CONSTRAINT uk_branch_holiday UNIQUE (salon_id, holiday_date)
);

CREATE TABLE salon_whatsapp_config (
    id               BIGSERIAL PRIMARY KEY,
    salon_id         BIGINT       NOT NULL UNIQUE REFERENCES salon(id) ON DELETE CASCADE,
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    token_enc        TEXT,
    phone_number_id  VARCHAR(64),
    business_account_id VARCHAR(64),
    salon_phone_e164 VARCHAR(32),
    webhook_verify_token VARCHAR(128),
    updated_at       TIMESTAMP
);
