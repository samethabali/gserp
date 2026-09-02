-- V27: chk_appointment_status kısıtına PENDING_APPROVAL değerini ekle

-- V9 ile AppointmentStatus enum'una PENDING_APPROVAL eklendi (public booking
-- üzerinden gelen onay bekleyen randevular), ancak V6'da tanımlanan
-- chk_appointment_status kısıtı güncellenmediği için bu statüdeki kayıtlar
-- INSERT sırasında kısıta takılıyordu.

ALTER TABLE appointment DROP CONSTRAINT IF EXISTS chk_appointment_status;

ALTER TABLE appointment
    ADD CONSTRAINT chk_appointment_status
    CHECK (status IN ('PENDING_APPROVAL', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));
