-- V3: Ödeme tablosu ve müşteri bakiye alanı
-- Özellik 3: Tahsilat (nakit/kart)
-- Özellik 6: Veresiye ödeme
-- Özellik 11: Müşteri bakiye/borç sistemi

ALTER TABLE customer ADD COLUMN balance NUMERIC(12,2) NOT NULL DEFAULT 0;

CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    appointment_id  BIGINT NOT NULL REFERENCES appointment(id) ON DELETE CASCADE,
    customer_name   VARCHAR(255),
    customer_phone  VARCHAR(64),
    amount          NUMERIC(12,2) NOT NULL,
    method          VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PAID',
    deferred_note   TEXT,
    collected_at    TIMESTAMP,
    staff_id        BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_appointment  ON payment(appointment_id);
CREATE INDEX idx_payment_collected_at ON payment(collected_at);
CREATE INDEX idx_payment_customer     ON payment(customer_phone);
