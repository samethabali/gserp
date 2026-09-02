-- V9: Müşteri Portalı — müşteri girişi, randevu isteği sistemi

-- 1. AppointmentStatus için PENDING_APPROVAL değeri JPA enum'unda eklendi.
--    DB tarafında status sütunu VARCHAR(32) olarak saklandığı için ek bir DDL gerekmiyor.

-- 2. users tablosuna customer_id FK ekle (müşteri kullanıcıları için)
ALTER TABLE users ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_customer
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_users_customer_id ON users(customer_id);

-- 3. customer.email alanına unique constraint ekle (login kimliği)
ALTER TABLE customer ADD CONSTRAINT uq_customer_email UNIQUE (email);

-- 4. users tablosundaki role sütununu VARCHAR'a çevir (CUSTOMER değeri için)
--    PostgreSQL'de enum yerine string saklıyoruz (JPA @Enumerated(STRING) ile uyumlu)
--    Zaten VARCHAR(32) olarak tanımlı — sadece CUSTOMER değerine izin veriyoruz.
--    (CHECK constraint opsiyonel, JPA seviyesinde validate edilir)
