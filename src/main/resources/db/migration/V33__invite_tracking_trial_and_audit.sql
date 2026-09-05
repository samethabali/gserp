-- V33: davet kodu kullanım geçmişi, kod başına deneme süresi, log kapsamı genişletme
--
-- Üç ayrı ihtiyaç aynı sürümde toplandı çünkü üçü de ilk gerçek müşteri için
-- aynı akışı (davet -> kayıt -> takip) çalışır hâle getiriyor.

-- ─────────────── 1. Davet kodu: kod başına ücretsiz kullanım süresi ───────────────
-- Deneme süresi SalonProvisioningService içinde 14 güne sabitlenmişti; davet
-- sahibine 3 ay verebilmek için süre kodun kendisinde tutuluyor.
ALTER TABLE invite_code ADD COLUMN IF NOT EXISTS trial_days INT NOT NULL DEFAULT 90;

ALTER TABLE invite_code
    ADD CONSTRAINT chk_invite_trial_days CHECK (trial_days BETWEEN 1 AND 365);

-- ─────────────── 2. Davet kullanım geçmişi ───────────────
-- redeemed_organization_id tek kolondu ve her kullanımda üzerine yazılıyordu:
-- max_uses > 1 olan bir kodda yalnızca son kullanan işletme görünüyordu.
CREATE TABLE invite_redemption (
    id               BIGSERIAL PRIMARY KEY,
    invite_code_id   BIGINT      NOT NULL REFERENCES invite_code(id),
    organization_id  BIGINT      NOT NULL REFERENCES organization(id),
    salon_id         BIGINT      NOT NULL REFERENCES salon(id),
    salon_slug       VARCHAR(64) NOT NULL,
    admin_user_id    BIGINT      REFERENCES users(id),
    ip               VARCHAR(64),
    redeemed_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invite_redemption_code ON invite_redemption (invite_code_id, redeemed_at DESC);
CREATE UNIQUE INDEX uk_invite_redemption_org ON invite_redemption (organization_id);

-- Mevcut (varsa) tekil kayıtları geçmiş tablosuna taşı, sonra kolonu düşür.
-- Bir organizasyonun birden fazla salonu olabilir; kaydı ilk salona bağla ki
-- uk_invite_redemption_org tekilliği bozulmasın.
INSERT INTO invite_redemption (invite_code_id, organization_id, salon_id, salon_slug, redeemed_at)
SELECT DISTINCT ON (i.redeemed_organization_id)
       i.id, i.redeemed_organization_id, s.id, s.slug, i.created_at
FROM invite_code i
JOIN salon s ON s.organization_id = i.redeemed_organization_id
WHERE i.redeemed_organization_id IS NOT NULL
ORDER BY i.redeemed_organization_id, s.id;

ALTER TABLE invite_code DROP COLUMN IF EXISTS redeemed_organization_id;

-- Ters arama: "bu işletme hangi kodla geldi?"
ALTER TABLE organization ADD COLUMN IF NOT EXISTS invite_code_id BIGINT REFERENCES invite_code(id);

UPDATE organization o
SET invite_code_id = r.invite_code_id
FROM invite_redemption r
WHERE r.organization_id = o.id AND o.invite_code_id IS NULL;

-- ─────────────── 3. Log kapsamı ───────────────
-- Platform admin işlemlerinde tenant bağlamı yok; salon_id NOT NULL olduğu için
-- bu işlemler hiç loglanamıyordu.
ALTER TABLE activity_event ALTER COLUMN salon_id DROP NOT NULL;

ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS scope       VARCHAR(16) NOT NULL DEFAULT 'TENANT';
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS outcome     VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS http_status INT;

ALTER TABLE activity_event
    ADD CONSTRAINT chk_activity_scope CHECK (scope IN ('TENANT', 'PLATFORM')),
    ADD CONSTRAINT chk_activity_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'ERROR'));

-- action alanı artık LOGIN_SUCCESS / PASSWORD_CHANGE gibi daha uzun değerler taşıyor.
ALTER TABLE activity_event ALTER COLUMN action TYPE VARCHAR(32);

CREATE INDEX idx_activity_scope_created ON activity_event (scope, created_at DESC);
CREATE INDEX idx_activity_actor_created ON activity_event (actor_user_id, created_at DESC);

-- ─────────────── 4. Personel kullanıcı adı global benzersiz ───────────────
-- Giriş artık kiracı bilmeden çözülüyor (subdomain kaldırıldı). Müşteri portalı
-- kullanıcıları e-posta/telefonla kayıt olduğu için salon bazlı kalır.
CREATE UNIQUE INDEX uk_users_username_staff ON users (username) WHERE role <> 'CUSTOMER';
