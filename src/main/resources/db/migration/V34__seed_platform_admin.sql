-- V34: Allow platform-admin users without a salon and seed a default one

-- Platform admin, herhangi bir salon'a bağlı olmadan çalışır.
-- V15 tüm salon_id'leri NOT NULL yapmıştı; PLATFORM_ADMIN için bunu gevşetiyoruz.
ALTER TABLE users ALTER COLUMN salon_id DROP NOT NULL;

INSERT INTO users (username, password_hash, role, enabled, must_change_password, created_at, password_changed_at, token_version)
SELECT 'platform_admin',
       '$2a$10$s8Ka9SZOGK7ORutuWhD1M.tLzITB0/91wVclUFa4JWlvxcNCtIjj.', -- Password: Demo2026!
       'PLATFORM_ADMIN',
       true,
       false,
       current_timestamp,
       current_timestamp,
       0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'platform_admin');
