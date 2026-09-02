-- Salon white-label ayarları
CREATE TABLE salon_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(64)  NOT NULL UNIQUE,
    setting_value TEXT,
    updated_at    TIMESTAMP
);

INSERT INTO salon_settings (setting_key, setting_value, updated_at) VALUES
    ('salon.name', 'GSERP Salon', NOW()),
    ('salon.logo_url', '', NOW()),
    ('salon.primary_color', '#e91e8c', NOW());

-- KVKK onay zamanı
ALTER TABLE customer ADD COLUMN IF NOT EXISTS consent_at TIMESTAMP;
