-- V24: Consent registry + salon contact fields

CREATE TABLE consent_record (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    salon_id     BIGINT       NOT NULL REFERENCES salon(id),
    consent_type VARCHAR(32)  NOT NULL CHECK (consent_type IN ('PRIVACY', 'MARKETING', 'REMINDER')),
    version      VARCHAR(16)  NOT NULL DEFAULT '1.0',
    granted_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at   TIMESTAMP
);
CREATE INDEX idx_consent_customer ON consent_record(customer_id);
CREATE INDEX idx_consent_salon ON consent_record(salon_id);

ALTER TABLE salon ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255);
ALTER TABLE salon ADD COLUMN IF NOT EXISTS dpo_name VARCHAR(255);

UPDATE salon SET contact_email = 'info@gserp.local' WHERE id = 1 AND contact_email IS NULL;

-- Migrate existing consent_at to consent_record
INSERT INTO consent_record (customer_id, salon_id, consent_type, version, granted_at)
SELECT id, salon_id, 'PRIVACY', '1.0', consent_at
FROM customer WHERE consent_at IS NOT NULL;
