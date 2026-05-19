-- V6: Eksik FK, UNIQUE, INDEX ve CHECK constraint'leri ekleme
-- Bu migration mevcut veri bütünlüğü sorunlarını giderir.

-- ============================================================
-- 1. EKSİK FOREIGN KEY'LER
-- ============================================================

-- appointment.staff_id → staff(id) — personel silinmeden önce randevular temizlenmeli
ALTER TABLE appointment
    ADD CONSTRAINT fk_appointment_staff
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE RESTRICT;

-- appointment.service_id → service_definition(id)
ALTER TABLE appointment
    ADD CONSTRAINT fk_appointment_service
    FOREIGN KEY (service_id) REFERENCES service_definition(id) ON DELETE RESTRICT;

-- working_hours.staff_id → staff(id) — personel silinirse çalışma saatleri de silinir
ALTER TABLE working_hours
    ADD CONSTRAINT fk_working_hours_staff
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE;

-- waitlist_entry.service_id → service_definition(id)
ALTER TABLE waitlist_entry
    ADD CONSTRAINT fk_waitlist_service
    FOREIGN KEY (service_id) REFERENCES service_definition(id) ON DELETE SET NULL;

-- waitlist_entry.preferred_staff_id → staff(id)
ALTER TABLE waitlist_entry
    ADD CONSTRAINT fk_waitlist_staff
    FOREIGN KEY (preferred_staff_id) REFERENCES staff(id) ON DELETE SET NULL;

-- payment.staff_id → staff(id) — ödeme kaydı korunur
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_staff
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL;

-- expense.staff_id → staff(id)
ALTER TABLE expense
    ADD CONSTRAINT fk_expense_staff
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL;

-- product_sale.staff_id → staff(id)
ALTER TABLE product_sale
    ADD CONSTRAINT fk_product_sale_staff
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL;

-- service_required_resources.resource_id → resource(id)
ALTER TABLE service_required_resources
    ADD CONSTRAINT fk_srr_resource
    FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE CASCADE;

-- appointment_resources.resource_id → resource(id)
ALTER TABLE appointment_resources
    ADD CONSTRAINT fk_appt_resources_resource
    FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE CASCADE;

-- ============================================================
-- 2. EKSİK UNIQUE CONSTRAINT'LER
-- ============================================================

-- customer.phone — NULL değerler hariç, aynı telefon birden fazla girilemesin
CREATE UNIQUE INDEX uk_customer_phone ON customer(phone) WHERE phone IS NOT NULL;

-- working_hours(staff_id, day_of_week) — aynı personel+gün için çift kayıt engellensin
ALTER TABLE working_hours
    ADD CONSTRAINT uk_working_hours_staff_day
    UNIQUE (staff_id, day_of_week);

-- ============================================================
-- 3. EKSİK PERFORMANS İNDEKSLERİ
-- ============================================================

-- appointment.customer_phone — müşteri geçmişi sorguları için
CREATE INDEX idx_appointment_customer_phone ON appointment(customer_phone);

-- appointment.session_group_id — seans grubu sorguları için
CREATE INDEX idx_appointment_session_group ON appointment(session_group_id) WHERE session_group_id IS NOT NULL;

-- appointment.status — durum filtreleme ve dashboard sorguları için
CREATE INDEX idx_appointment_status ON appointment(status);

-- customer.phone — müşteri arama sorguları için
CREATE INDEX idx_customer_phone ON customer(phone) WHERE phone IS NOT NULL;

-- waitlist_entry.fulfilled — aktif bekleme listesi sorguları için
CREATE INDEX idx_waitlist_fulfilled ON waitlist_entry(fulfilled) WHERE fulfilled = FALSE;

-- ============================================================
-- 4. EKSİK CHECK CONSTRAINT'LER (Enum Validasyonu)
-- ============================================================

-- staff.role
ALTER TABLE staff
    ADD CONSTRAINT chk_staff_role
    CHECK (role IN ('ADMIN', 'RECEPTIONIST', 'SPECIALIST'));

-- resource.resource_type
ALTER TABLE resource
    ADD CONSTRAINT chk_resource_type
    CHECK (resource_type IN ('ROOM', 'DEVICE', 'EQUIPMENT'));

-- service_definition.category
ALTER TABLE service_definition
    ADD CONSTRAINT chk_service_category
    CHECK (category IN ('HAIR', 'NAIL', 'SKIN', 'LASER', 'OTHER'));

-- appointment.status
ALTER TABLE appointment
    ADD CONSTRAINT chk_appointment_status
    CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));

-- payment.method
ALTER TABLE payment
    ADD CONSTRAINT chk_payment_method
    CHECK (method IN ('CASH', 'CARD'));

-- payment.status
ALTER TABLE payment
    ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('PAID', 'DEFERRED', 'PARTIAL'));

-- expense.category
ALTER TABLE expense
    ADD CONSTRAINT chk_expense_category
    CHECK (category IN ('RENT', 'UTILITIES', 'SUPPLIES', 'SALARY', 'MAINTENANCE', 'OTHER'));

-- appointment_flag.flag_type
ALTER TABLE appointment_flag
    ADD CONSTRAINT chk_flag_type
    CHECK (flag_type IN ('ALLERGY', 'PREFERENCE', 'VIP', 'DRINK', 'CUSTOM'));

-- ============================================================
-- 5. EKSİK CASCADE KURALI DÜZELTMESİ
-- ============================================================

-- product_sale.product_id — mevcut FK'yı düzelt, cascade ekle
ALTER TABLE product_sale
    DROP CONSTRAINT IF EXISTS product_sale_product_id_fkey;

ALTER TABLE product_sale
    ADD CONSTRAINT fk_product_sale_product
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT;
