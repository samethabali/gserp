-- V17: Auth roles — user_salon_role, organization_owner, org_id on users

ALTER TABLE users ADD COLUMN IF NOT EXISTS organization_id BIGINT REFERENCES organization(id);

CREATE TABLE user_salon_role (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    salon_id     BIGINT      NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    role         VARCHAR(32) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    assigned_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_salon_role UNIQUE (user_id, salon_id, role)
);
CREATE INDEX idx_user_salon_role_user ON user_salon_role(user_id);
CREATE INDEX idx_user_salon_role_salon ON user_salon_role(salon_id);

CREATE TABLE organization_owner (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_org_owner UNIQUE (organization_id, user_id)
);

-- Migrate existing ADMIN users to BRANCH_MANAGER role name in DB
UPDATE users SET role = 'BRANCH_MANAGER' WHERE role = 'ADMIN';

-- Backfill user_salon_role from existing users
INSERT INTO user_salon_role (user_id, salon_id, role, active, assigned_at)
SELECT id, salon_id, role, enabled, COALESCE(created_at, NOW())
FROM users
WHERE role NOT IN ('CUSTOMER', 'PLATFORM_ADMIN')
ON CONFLICT DO NOTHING;

-- Set organization_id on users from salon
UPDATE users u SET organization_id = s.organization_id
FROM salon s WHERE u.salon_id = s.id AND u.organization_id IS NULL;
