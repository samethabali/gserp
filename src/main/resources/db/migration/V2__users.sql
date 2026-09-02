-- GSERP — Aşama 2: kimlik doğrulama tabloları

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    staff_id      BIGINT,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT fk_users_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL
);
CREATE INDEX idx_users_username ON users(username);
