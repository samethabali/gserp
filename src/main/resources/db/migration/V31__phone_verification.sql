-- V31: Telefon doğrulama (OTP) altyapısı
--
-- Online randevuda numaranın gerçekten arayana ait olduğu hiç doğrulanmıyordu:
-- herkes başkasının numarasıyla randevu alabiliyordu. Bu göç mekanizmayı kurar;
-- gönderim sağlayıcısı ayrı bir konudur ve varsayılan olarak KAPALIDIR
-- (salon ayarı booking.sms_verification_enabled, varsayılan "false").
--
-- Not: V13'teki notification_log tablosu V28'de kaldırılmıştı; burada onu
-- diriltmiyoruz, birinci günden salon_id taşıyan yenisini kuruyoruz.

CREATE TABLE verification_code (
    id                 BIGSERIAL PRIMARY KEY,
    salon_id           BIGINT      NOT NULL REFERENCES salon(id),
    phone_normalized   VARCHAR(32) NOT NULL,
    -- Kod düz metin saklanmaz: 3 dakikalık ömür + 5 deneme sınırı çevrimiçi riski
    -- zaten kapatıyor, hash veritabanı sızıntısı hâlinde çevrimdışı riski de kapatır.
    code_hash          VARCHAR(72) NOT NULL,
    purpose            VARCHAR(32) NOT NULL DEFAULT 'BOOKING',
    attempts           INTEGER     NOT NULL DEFAULT 0,
    max_attempts       INTEGER     NOT NULL DEFAULT 5,
    verified_at        TIMESTAMP,
    consumed_at        TIMESTAMP,
    -- Doğrulanmış durumu randevu POST'una taşıyan tek kullanımlık kulp.
    verification_token VARCHAR(64),
    request_ip         VARCHAR(64),
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMP   NOT NULL
);

CREATE INDEX idx_vcode_salon_phone ON verification_code(salon_id, phone_normalized, created_at DESC);
CREATE UNIQUE INDEX uk_vcode_token ON verification_code(verification_token) WHERE verification_token IS NOT NULL;
CREATE INDEX idx_vcode_expires ON verification_code(expires_at);

CREATE TABLE sms_log (
    id            BIGSERIAL PRIMARY KEY,
    salon_id      BIGINT      NOT NULL REFERENCES salon(id),
    channel       VARCHAR(32) NOT NULL DEFAULT 'SMS',
    template_name VARCHAR(64),
    recipient     VARCHAR(32),
    status        VARCHAR(32) NOT NULL,
    provider      VARCHAR(32),
    provider_ref  VARCHAR(128),
    error_message TEXT,
    sent_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sms_log_salon_sent ON sms_log(salon_id, sent_at DESC);
