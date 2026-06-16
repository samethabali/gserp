-- V21: Organization loyalty policy

ALTER TABLE organization ADD COLUMN IF NOT EXISTS loyalty_policy VARCHAR(16) NOT NULL DEFAULT 'SALON'
    CHECK (loyalty_policy IN ('SALON', 'ORG'));
