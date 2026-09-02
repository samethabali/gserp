-- V29: Aynı uzmana çakışan randevu yazılmasını veritabanı düzeyinde engelle
--
-- Müsaitlik kontrolü ile kaydın yazılması arasında kilit yoktu: iki eşzamanlı
-- istek kontrolü aynı anda geçip ikisi de yazılabiliyordu (public booking'de
-- 5 eşzamanlı istekten 2'si aynı slota düşüyordu). Servis tarafındaki kontrol
-- duruyor; bu kısıt son savunma hattı.
--
-- Exclusion constraint, eşsiz index'in aksine yalnızca aynı başlangıç saatini
-- değil, kesişen her aralığı reddeder. İptal edilen randevular slotu bırakır.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointment
    ADD CONSTRAINT excl_appointment_staff_overlap
    EXCLUDE USING gist (
        salon_id WITH =,
        staff_id WITH =,
        tsrange(start_time, end_time) WITH &&
    ) WHERE (status <> 'CANCELLED');
