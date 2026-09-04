-- V35: Seed a simple super-admin user for invite code management
-- Username: admin  |  Password: admin123

INSERT INTO users (username, password_hash, role, enabled, must_change_password, created_at, password_changed_at, token_version)
SELECT 'admin',
       '$2a$10$7rRFactq27LIfLMx3U/37umBlabzh.OU4gF/t3nHzAMUIvmZsO3nq', -- Password: admin123
       'PLATFORM_ADMIN',
       true,
       false,
       current_timestamp,
       current_timestamp,
       0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin' AND role = 'PLATFORM_ADMIN');
