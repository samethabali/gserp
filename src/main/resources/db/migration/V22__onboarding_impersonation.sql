-- V22: Onboarding and impersonation audit

CREATE TABLE impersonation_log (
    id               BIGSERIAL PRIMARY KEY,
    platform_user_id BIGINT    NOT NULL REFERENCES users(id),
    target_user_id   BIGINT    NOT NULL REFERENCES users(id),
    salon_id         BIGINT    REFERENCES salon(id),
    started_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at         TIMESTAMP
);

CREATE TABLE onboarding_state (
    id           BIGSERIAL PRIMARY KEY,
    salon_id     BIGINT      NOT NULL UNIQUE REFERENCES salon(id) ON DELETE CASCADE,
    current_step VARCHAR(32) NOT NULL DEFAULT 'SALON_INFO',
    completed_at TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);
