-- V28: WhatsApp ve iyzico entegrasyonlarının veritabanı izlerini kaldır
--
-- Her iki entegrasyon da üründen tamamen çıkarıldı. Bu migration geride kalan
-- tablo, sütun ve satırları temizler; uygulama tarafında karşılıkları zaten yok.

-- WhatsApp gönderim kaydı — yalnızca WhatsApp istemcisi yazıyordu.
DROP TABLE IF EXISTS notification_log;

-- Salon bazlı WhatsApp API yapılandırması (şifreli token'lar dahil).
DROP TABLE IF EXISTS salon_whatsapp_config;

-- Plan başına WhatsApp mesaj kotası.
ALTER TABLE subscription_plan DROP COLUMN IF EXISTS whatsapp_quota;

-- Kullanım sayacındaki WhatsApp metrik satırları.
DELETE FROM usage_meter WHERE metric = 'whatsapp_sent';

-- iyzico webhook'larından gelen billing event kayıtları.
DELETE FROM billing_event WHERE event_type = 'IYZICO_WEBHOOK';

-- Kurulum sihirbazında kalan WHATSAPP adımı; artık böyle bir adım yok.
UPDATE onboarding_state SET current_step = 'COMPLETED' WHERE current_step = 'WHATSAPP';
