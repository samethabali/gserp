-- V10: Kampanya / Kupon / Sadakat Sistemi

-- 1. Kupon tablosu
CREATE TABLE coupon (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL UNIQUE,
    description TEXT,
    discount_type  VARCHAR(16) NOT NULL CHECK (discount_type IN ('PERCENTAGE','FIXED')),
    discount_value NUMERIC(10,2) NOT NULL CHECK (discount_value > 0),
    min_appointments INT NOT NULL DEFAULT 0,
    valid_from  TIMESTAMP,
    valid_until TIMESTAMP,
    max_uses    INT,
    used_count  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2. Sadakat eşikleri
CREATE TABLE loyalty_tier (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(64) NOT NULL,
    min_completed       INT NOT NULL,
    discount_percentage NUMERIC(5,2) NOT NULL CHECK (discount_percentage >= 0),
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

-- 3. Kupon kullanım geçmişi
CREATE TABLE coupon_usage (
    id             BIGSERIAL PRIMARY KEY,
    coupon_id      BIGINT NOT NULL REFERENCES coupon(id),
    customer_id    BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    appointment_id BIGINT REFERENCES appointment(id) ON DELETE SET NULL,
    used_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_usage_coupon    ON coupon_usage(coupon_id);
CREATE INDEX idx_coupon_usage_customer  ON coupon_usage(customer_id);

-- 4. Örnek sadakat eşikleri
INSERT INTO loyalty_tier (name, min_completed, discount_percentage) VALUES
    ('Gümüş Üye',   5,  5.00),
    ('Altın Üye',  10, 10.00),
    ('VIP Üye',    20, 15.00);
