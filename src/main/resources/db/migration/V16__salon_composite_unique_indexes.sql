-- V16: Composite unique constraints and tenant-scoped indexes

-- users: per-salon username
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_username;
CREATE UNIQUE INDEX uk_users_salon_username ON users(salon_id, username);

-- customer: per-salon phone and email
DROP INDEX IF EXISTS uk_customer_phone;
CREATE UNIQUE INDEX uk_customer_salon_phone ON customer(salon_id, phone) WHERE phone IS NOT NULL;
ALTER TABLE customer DROP CONSTRAINT IF EXISTS uq_customer_email;
CREATE UNIQUE INDEX uk_customer_salon_email ON customer(salon_id, email) WHERE email IS NOT NULL;

-- coupon: per-salon code
ALTER TABLE coupon DROP CONSTRAINT IF EXISTS coupon_code_key;
CREATE UNIQUE INDEX uk_coupon_salon_code ON coupon(salon_id, code);

-- salon_settings: per-salon key
ALTER TABLE salon_settings DROP CONSTRAINT IF EXISTS salon_settings_setting_key_key;
CREATE UNIQUE INDEX uk_salon_settings_salon_key ON salon_settings(salon_id, setting_key);

-- Performance indexes
CREATE INDEX idx_appointment_salon_staff_start ON appointment(salon_id, staff_id, start_time);
CREATE INDEX idx_appointment_salon_start ON appointment(salon_id, start_time);
CREATE INDEX idx_customer_salon_phone ON customer(salon_id, phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_staff_salon ON staff(salon_id);
CREATE INDEX idx_service_salon ON service_definition(salon_id);
CREATE INDEX idx_notification_salon ON notification_log(salon_id);
