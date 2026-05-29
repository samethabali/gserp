-- V7: Geliştirme ortamı için gerçekçi mock veri
-- Güzellik Salonu ERP — Türkçe isimler ve gerçekçi senaryolar

-- ============================================================
-- 1. PERSONEL (Staff)
-- ============================================================
INSERT INTO staff (name, phone, email, role, color_hex, active, created_at, updated_at) VALUES
    ('Ayşe Yılmaz',    '05321001001', 'ayse@gserp.com',    'SPECIALIST',   '#E91E63', true, NOW() - INTERVAL '90 days', NOW()),
    ('Fatma Demir',     '05321001002', 'fatma@gserp.com',   'SPECIALIST',   '#9C27B0', true, NOW() - INTERVAL '90 days', NOW()),
    ('Zeynep Kaya',     '05321001003', 'zeynep@gserp.com',  'SPECIALIST',   '#2196F3', true, NOW() - INTERVAL '90 days', NOW()),
    ('Elif Çelik',      '05321001004', 'elif@gserp.com',    'SPECIALIST',   '#4CAF50', true, NOW() - INTERVAL '60 days', NOW()),
    ('Merve Şahin',     '05321001005', 'merve@gserp.com',   'RECEPTIONIST', '#FF9800', true, NOW() - INTERVAL '90 days', NOW()),
    ('Ahmet Öztürk',    '05321001006', 'ahmet@gserp.com',   'ADMIN',        '#607D8B', true, NOW() - INTERVAL '90 days', NOW());

-- ============================================================
-- 2. KAYNAKLAR (Resources)
-- ============================================================
INSERT INTO resource (name, resource_type, capacity, active, created_at) VALUES
    ('Cilt Bakım Odası 1',   'ROOM',      1, true, NOW() - INTERVAL '90 days'),
    ('Cilt Bakım Odası 2',   'ROOM',      1, true, NOW() - INTERVAL '90 days'),
    ('Lazer Odası',           'ROOM',      1, true, NOW() - INTERVAL '90 days'),
    ('Saç Bakım Odası 1',    'ROOM',      2, true, NOW() - INTERVAL '90 days'),
    ('Saç Bakım Odası 2',    'ROOM',      2, true, NOW() - INTERVAL '90 days'),
    ('Lazer Cihazı',          'DEVICE',    1, true, NOW() - INTERVAL '90 days'),
    ('IPL Cihazı',            'DEVICE',    1, true, NOW() - INTERVAL '90 days'),
    ('Tırnak Seti 1',         'EQUIPMENT', 1, true, NOW() - INTERVAL '90 days'),
    ('Tırnak Seti 2',         'EQUIPMENT', 1, true, NOW() - INTERVAL '90 days'),
    ('Fön Makinesi Ekstra',   'EQUIPMENT', 1, true, NOW() - INTERVAL '90 days');

-- ============================================================
-- 3. HİZMET TANIMLARI (Service Definitions)
-- ============================================================
INSERT INTO service_definition (name, duration_minutes, base_price, category, requires_resource, active, created_at) VALUES
    ('Saç Kesim',           45,   300.00,  'HAIR',  false, true, NOW() - INTERVAL '90 days'),
    ('Saç Boyama',          90,   800.00,  'HAIR',  true,  true, NOW() - INTERVAL '90 days'),
    ('Fön',                 30,   200.00,  'HAIR',  false, true, NOW() - INTERVAL '90 days'),
    ('Keratin Bakım',      120,  1500.00,  'HAIR',  true,  true, NOW() - INTERVAL '90 days'),
    ('Manikür',             45,   250.00,  'NAIL',  true,  true, NOW() - INTERVAL '90 days'),
    ('Pedikür',             60,   350.00,  'NAIL',  true,  true, NOW() - INTERVAL '90 days'),
    ('Protez Tırnak',       90,   600.00,  'NAIL',  true,  true, NOW() - INTERVAL '90 days'),
    ('Cilt Bakımı',         60,   500.00,  'SKIN',  true,  true, NOW() - INTERVAL '90 days'),
    ('Cilt Temizliği',      45,   400.00,  'SKIN',  true,  true, NOW() - INTERVAL '90 days'),
    ('Hydrafacial',         75,   900.00,  'SKIN',  true,  true, NOW() - INTERVAL '60 days'),
    ('Lazer Epilasyon',     30,   600.00,  'LASER', true,  true, NOW() - INTERVAL '90 days'),
    ('Alexandrit Lazer',    45,   900.00,  'LASER', true,  true, NOW() - INTERVAL '90 days'),
    ('Kaş Kontür',          60,   450.00,  'OTHER', false, true, NOW() - INTERVAL '30 days'),
    ('Kirpik Lifting',      45,   350.00,  'OTHER', false, true, NOW() - INTERVAL '30 days');

-- ============================================================
-- 4. HİZMET — KAYNAK İLİŞKİLERİ
-- ============================================================
-- Saç Boyama → Saç Bakım Odası 1 veya 2 (alternatifler)
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Saç Boyama' AND r.name IN ('Saç Bakım Odası 1', 'Saç Bakım Odası 2');

-- Keratin Bakım → Saç Bakım Odası 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Keratin Bakım' AND r.name IN ('Saç Bakım Odası 1', 'Saç Bakım Odası 2');

-- Manikür → Tırnak Seti 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Manikür' AND r.name IN ('Tırnak Seti 1', 'Tırnak Seti 2');

-- Pedikür → Tırnak Seti 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Pedikür' AND r.name IN ('Tırnak Seti 1', 'Tırnak Seti 2');

-- Protez Tırnak → Tırnak Seti 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Protez Tırnak' AND r.name IN ('Tırnak Seti 1', 'Tırnak Seti 2');

-- Cilt Bakımı → Cilt Bakım Odası 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Cilt Bakımı' AND r.name IN ('Cilt Bakım Odası 1', 'Cilt Bakım Odası 2');

-- Cilt Temizliği → Cilt Bakım Odası 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Cilt Temizliği' AND r.name IN ('Cilt Bakım Odası 1', 'Cilt Bakım Odası 2');

-- Hydrafacial → Cilt Bakım Odası 1 veya 2
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Hydrafacial' AND r.name IN ('Cilt Bakım Odası 1', 'Cilt Bakım Odası 2');

-- Lazer Epilasyon → Lazer Odası + Lazer Cihazı (ikisi de gerekli)
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Lazer Epilasyon' AND r.name IN ('Lazer Odası', 'Lazer Cihazı');

-- Alexandrit Lazer → Lazer Odası + IPL Cihazı
INSERT INTO service_required_resources (service_id, resource_id)
SELECT sd.id, r.id FROM service_definition sd, resource r
WHERE sd.name = 'Alexandrit Lazer' AND r.name IN ('Lazer Odası', 'IPL Cihazı');

-- ============================================================
-- 5. PERSONEL UZMANLIK ALANLARI
-- ============================================================
-- Ayşe → Saç
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'HAIR' FROM staff WHERE name = 'Ayşe Yılmaz';

-- Fatma → Cilt + Lazer
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'SKIN' FROM staff WHERE name = 'Fatma Demir';
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'LASER' FROM staff WHERE name = 'Fatma Demir';

-- Zeynep → Tırnak
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'NAIL' FROM staff WHERE name = 'Zeynep Kaya';

-- Elif → Saç + Cilt
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'HAIR' FROM staff WHERE name = 'Elif Çelik';
INSERT INTO staff_specialization (staff_id, service_category)
SELECT id, 'SKIN' FROM staff WHERE name = 'Elif Çelik';

-- ============================================================
-- 6. MÜŞTERİLER (15 kişi)
-- ============================================================
INSERT INTO customer (first_name, last_name, phone, email, notes, balance, created_at, updated_at) VALUES
    ('Selin',    'Arslan',    '05551110001', 'selin.arslan@gmail.com',    'Düzenli müşteri, her hafta geliyor',            0.00,    NOW() - INTERVAL '80 days', NOW()),
    ('Deniz',    'Yıldız',    '05551110002', 'deniz.yildiz@gmail.com',    NULL,                                             0.00,    NOW() - INTERVAL '75 days', NOW()),
    ('Büşra',    'Aktaş',     '05551110003', 'busra.aktas@gmail.com',     'Hassas cilt, dikkat edilmeli',                   0.00,    NOW() - INTERVAL '70 days', NOW()),
    ('Gamze',    'Özkan',     '05551110004', NULL,                         'Lazer paketi devam ediyor',                    -600.00,   NOW() - INTERVAL '65 days', NOW()),
    ('Hülya',    'Koç',       '05551110005', 'hulya.koc@hotmail.com',     NULL,                                             0.00,    NOW() - INTERVAL '60 days', NOW()),
    ('İrem',     'Polat',     '05551110006', 'irem.polat@gmail.com',      'VIP müşteri',                                  200.00,    NOW() - INTERVAL '55 days', NOW()),
    ('Cansu',    'Erdoğan',   '05551110007', NULL,                         NULL,                                             0.00,    NOW() - INTERVAL '50 days', NOW()),
    ('Melis',    'Aydın',     '05551110008', 'melis.aydin@gmail.com',     'Alerji: Lateks',                                 0.00,    NOW() - INTERVAL '45 days', NOW()),
    ('Tuğçe',    'Şen',       '05551110009', NULL,                         'Keratin bakım paketi aldı',                      0.00,    NOW() - INTERVAL '40 days', NOW()),
    ('Pınar',    'Güneş',     '05551110010', 'pinar.gunes@gmail.com',    NULL,                                             0.00,    NOW() - INTERVAL '35 days', NOW()),
    ('Esra',     'Yılmaz',    '05551110011', 'esra.yilmaz@yahoo.com',    'Çay tercih ediyor',                              0.00,    NOW() - INTERVAL '30 days', NOW()),
    ('Sibel',    'Özer',      '05551110012', NULL,                         NULL,                                             0.00,    NOW() - INTERVAL '25 days', NOW()),
    ('Aslı',     'Çetin',     '05551110013', 'asli.cetin@gmail.com',     'Düğün hazırlığı — Haziran',                      0.00,    NOW() - INTERVAL '20 days', NOW()),
    ('Derya',    'Kaplan',    '05551110014', NULL,                         NULL,                                           -350.00,   NOW() - INTERVAL '15 days', NOW()),
    ('Nilgün',   'Bakır',     '05551110015', 'nilgun.bakir@gmail.com',   'Referans: Selin Arslan',                          0.00,    NOW() - INTERVAL '10 days', NOW());

-- ============================================================
-- 7. ÇALIŞMA SAATLERİ (4 uzman için Pzt-Cmt)
-- ============================================================

-- Ayşe Yılmaz — Pzt-Cmt 09:00-18:00, Pazar izin
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'MONDAY',    '09:00', '18:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'TUESDAY',   '09:00', '18:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'WEDNESDAY', '09:00', '18:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'THURSDAY',  '09:00', '18:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'FRIDAY',    '09:00', '18:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SATURDAY',  '10:00', '16:00', false FROM staff WHERE name = 'Ayşe Yılmaz';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SUNDAY',    NULL,    NULL,    true  FROM staff WHERE name = 'Ayşe Yılmaz';

-- Fatma Demir — Pzt-Cmt 10:00-19:00, Pazar izin
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'MONDAY',    '10:00', '19:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'TUESDAY',   '10:00', '19:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'WEDNESDAY', '10:00', '19:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'THURSDAY',  '10:00', '19:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'FRIDAY',    '10:00', '19:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SATURDAY',  '10:00', '16:00', false FROM staff WHERE name = 'Fatma Demir';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SUNDAY',    NULL,    NULL,    true  FROM staff WHERE name = 'Fatma Demir';

-- Zeynep Kaya — Sal-Cmt 09:30-17:30, Pzt+Paz izin
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'MONDAY',    NULL,    NULL,    true  FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'TUESDAY',   '09:30', '17:30', false FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'WEDNESDAY', '09:30', '17:30', false FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'THURSDAY',  '09:30', '17:30', false FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'FRIDAY',    '09:30', '17:30', false FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SATURDAY',  '10:00', '15:00', false FROM staff WHERE name = 'Zeynep Kaya';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SUNDAY',    NULL,    NULL,    true  FROM staff WHERE name = 'Zeynep Kaya';

-- Elif Çelik — Pzt-Cum 11:00-20:00, Cmt+Paz izin
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'MONDAY',    '11:00', '20:00', false FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'TUESDAY',   '11:00', '20:00', false FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'WEDNESDAY', '11:00', '20:00', false FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'THURSDAY',  '11:00', '20:00', false FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'FRIDAY',    '11:00', '20:00', false FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SATURDAY',  NULL,    NULL,    true  FROM staff WHERE name = 'Elif Çelik';
INSERT INTO working_hours (staff_id, day_of_week, start_time, end_time, day_off)
SELECT id, 'SUNDAY',    NULL,    NULL,    true  FROM staff WHERE name = 'Elif Çelik';

-- ============================================================
-- 8. KULLANICILAR (BCrypt hash: "admin123")
-- ============================================================
INSERT INTO users (username, password_hash, role, staff_id, enabled, created_at) VALUES
    ('admin',    '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'ADMIN',
     (SELECT id FROM staff WHERE name = 'Ahmet Öztürk'), true, NOW() - INTERVAL '90 days'),
    ('merve',    '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'RECEPTIONIST',
     (SELECT id FROM staff WHERE name = 'Merve Şahin'), true, NOW() - INTERVAL '90 days'),
    ('ayse',     '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'SPECIALIST',
     (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'), true, NOW() - INTERVAL '90 days'),
    ('fatma',    '$2a$10$xn3LI/AjqicFYZFruSwve.681477XaVNaUQbr1gioaWPn4t1KsnmG', 'SPECIALIST',
     (SELECT id FROM staff WHERE name = 'Fatma Demir'), true, NOW() - INTERVAL '90 days');

-- ============================================================
-- 9. RANDEVULAR (Geçmiş + Bugün + Gelecek)
-- ============================================================

-- ---- GEÇMİŞ RANDEVULAR (tamamlanmış) ----

-- 7 gün önce
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, internal_note, version, created_at, updated_at)
SELECT 'Selin Arslan', '05551110001',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       (CURRENT_DATE - INTERVAL '7 days') + TIME '09:00',
       (CURRENT_DATE - INTERVAL '7 days') + TIME '09:45',
       'COMPLETED', 300.00, 0, '', 300.00, 'Düzenli müşteri', 1, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'
FROM service_definition sd WHERE sd.name = 'Saç Kesim';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, version, created_at, updated_at)
SELECT 'Büşra Aktaş', '05551110003',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       (CURRENT_DATE - INTERVAL '7 days') + TIME '10:00',
       (CURRENT_DATE - INTERVAL '7 days') + TIME '11:00',
       'COMPLETED', 500.00, -50.00, 'İlk ziyaret indirimi', 450.00, 1, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'
FROM service_definition sd WHERE sd.name = 'Cilt Bakımı';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, version, created_at, updated_at)
SELECT 'Deniz Yıldız', '05551110002',
       (SELECT id FROM staff WHERE name = 'Zeynep Kaya'),
       sd.id,
       (CURRENT_DATE - INTERVAL '7 days') + TIME '11:00',
       (CURRENT_DATE - INTERVAL '7 days') + TIME '11:45',
       'COMPLETED', 250.00, 0, '', 250.00, 1, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'
FROM service_definition sd WHERE sd.name = 'Manikür';

-- 5 gün önce
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, version, created_at, updated_at)
SELECT 'Gamze Özkan', '05551110004',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       (CURRENT_DATE - INTERVAL '5 days') + TIME '14:00',
       (CURRENT_DATE - INTERVAL '5 days') + TIME '14:30',
       'COMPLETED', 600.00, 0, '', 600.00, 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
FROM service_definition sd WHERE sd.name = 'Lazer Epilasyon';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, version, created_at, updated_at)
SELECT 'Hülya Koç', '05551110005',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       (CURRENT_DATE - INTERVAL '5 days') + TIME '10:00',
       (CURRENT_DATE - INTERVAL '5 days') + TIME '11:30',
       'COMPLETED', 800.00, 0, '', 800.00, 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
FROM service_definition sd WHERE sd.name = 'Saç Boyama';

-- 3 gün önce — bir iptal ve bir no-show
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, cancellation_reason, version, created_at, updated_at)
SELECT 'Cansu Erdoğan', '05551110007',
       (SELECT id FROM staff WHERE name = 'Elif Çelik'),
       sd.id,
       (CURRENT_DATE - INTERVAL '3 days') + TIME '11:00',
       (CURRENT_DATE - INTERVAL '3 days') + TIME '12:00',
       'CANCELLED', 500.00, 500.00, 'Müşteri telefon ile iptal etti', 2, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'
FROM service_definition sd WHERE sd.name = 'Cilt Bakımı';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Sibel Özer', '05551110012',
       (SELECT id FROM staff WHERE name = 'Zeynep Kaya'),
       sd.id,
       (CURRENT_DATE - INTERVAL '3 days') + TIME '14:00',
       (CURRENT_DATE - INTERVAL '3 days') + TIME '14:45',
       'NO_SHOW', 250.00, 250.00, 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'
FROM service_definition sd WHERE sd.name = 'Manikür';

-- 2 gün önce — çoklu seans (lazer paketi, 6 seans)
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, session_group_id, session_number, total_sessions, version, created_at, updated_at)
SELECT 'İrem Polat', '05551110006',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       (CURRENT_DATE - INTERVAL '2 days') + TIME '10:00',
       (CURRENT_DATE - INTERVAL '2 days') + TIME '10:30',
       'COMPLETED', 900.00, 900.00, 'ABCD1234', 1, 6, 1, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
FROM service_definition sd WHERE sd.name = 'Alexandrit Lazer';

-- 1 gün önce
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, internal_note, version, created_at, updated_at)
SELECT 'Esra Yılmaz', '05551110011',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       (CURRENT_DATE - INTERVAL '1 day') + TIME '13:00',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '15:00',
       'COMPLETED', 1500.00, -200.00, 'Paket indirimi', 1300.00, 'İlk keratin bakımı', 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
FROM service_definition sd WHERE sd.name = 'Keratin Bakım';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Melis Aydın', '05551110008',
       (SELECT id FROM staff WHERE name = 'Zeynep Kaya'),
       sd.id,
       (CURRENT_DATE - INTERVAL '1 day') + TIME '09:30',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '10:30',
       'COMPLETED', 350.00, 350.00, 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
FROM service_definition sd WHERE sd.name = 'Pedikür';

-- ---- BUGÜNKÜ RANDEVULAR ----

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Selin Arslan', '05551110001',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       CURRENT_DATE + TIME '09:00',
       CURRENT_DATE + TIME '09:30',
       'COMPLETED', 200.00, 200.00, 1, CURRENT_DATE + TIME '08:00', NOW()
FROM service_definition sd WHERE sd.name = 'Fön';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Pınar Güneş', '05551110010',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       CURRENT_DATE + TIME '10:00',
       CURRENT_DATE + TIME '11:00',
       'COMPLETED', 500.00, 500.00, 1, CURRENT_DATE + TIME '09:00', NOW()
FROM service_definition sd WHERE sd.name = 'Cilt Bakımı';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Tuğçe Şen', '05551110009',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       CURRENT_DATE + TIME '11:00',
       CURRENT_DATE + TIME '11:45',
       'IN_PROGRESS', 300.00, 300.00, 1, CURRENT_DATE + TIME '09:30', NOW()
FROM service_definition sd WHERE sd.name = 'Saç Kesim';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Aslı Çetin', '05551110013',
       (SELECT id FROM staff WHERE name = 'Elif Çelik'),
       sd.id,
       CURRENT_DATE + TIME '14:00',
       CURRENT_DATE + TIME '15:30',
       'SCHEDULED', 800.00, 800.00, 0, CURRENT_DATE + TIME '08:00', NOW()
FROM service_definition sd WHERE sd.name = 'Saç Boyama';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Nilgün Bakır', '05551110015',
       (SELECT id FROM staff WHERE name = 'Zeynep Kaya'),
       sd.id,
       CURRENT_DATE + TIME '13:00',
       CURRENT_DATE + TIME '14:30',
       'SCHEDULED', 600.00, 600.00, 0, CURRENT_DATE + TIME '08:00', NOW()
FROM service_definition sd WHERE sd.name = 'Protez Tırnak';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Derya Kaplan', '05551110014',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       CURRENT_DATE + TIME '15:00',
       CURRENT_DATE + TIME '15:30',
       'SCHEDULED', 600.00, 600.00, 0, CURRENT_DATE + TIME '09:00', NOW()
FROM service_definition sd WHERE sd.name = 'Lazer Epilasyon';

-- ---- GELECEK RANDEVULAR ----

-- Yarın
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Hülya Koç', '05551110005',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       (CURRENT_DATE + INTERVAL '1 day') + TIME '10:00',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '10:45',
       'SCHEDULED', 300.00, 300.00, 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Saç Kesim';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Deniz Yıldız', '05551110002',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       (CURRENT_DATE + INTERVAL '1 day') + TIME '11:00',
       (CURRENT_DATE + INTERVAL '1 day') + TIME '12:15',
       'SCHEDULED', 900.00, 900.00, 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Hydrafacial';

-- 3 gün sonra
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Büşra Aktaş', '05551110003',
       (SELECT id FROM staff WHERE name = 'Elif Çelik'),
       sd.id,
       (CURRENT_DATE + INTERVAL '3 days') + TIME '14:00',
       (CURRENT_DATE + INTERVAL '3 days') + TIME '15:00',
       'SCHEDULED', 400.00, 400.00, 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Cilt Temizliği';

-- İrem Polat lazer seans 2/6 (gelecek hafta)
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, session_group_id, session_number, total_sessions, version, created_at, updated_at)
SELECT 'İrem Polat', '05551110006',
       (SELECT id FROM staff WHERE name = 'Fatma Demir'),
       sd.id,
       (CURRENT_DATE + INTERVAL '5 days') + TIME '10:00',
       (CURRENT_DATE + INTERVAL '5 days') + TIME '10:45',
       'SCHEDULED', 900.00, 900.00, 'ABCD1234', 2, 6, 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Alexandrit Lazer';

-- 1 hafta sonra
INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, final_price, version, created_at, updated_at)
SELECT 'Aslı Çetin', '05551110013',
       (SELECT id FROM staff WHERE name = 'Zeynep Kaya'),
       sd.id,
       (CURRENT_DATE + INTERVAL '7 days') + TIME '10:00',
       (CURRENT_DATE + INTERVAL '7 days') + TIME '10:45',
       'SCHEDULED', 250.00, 250.00, 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Manikür';

INSERT INTO appointment (customer_name, customer_phone, staff_id, service_id, start_time, end_time, status, base_price, adjustment, adjustment_note, final_price, internal_note, version, created_at, updated_at)
SELECT 'Gamze Özkan', '05551110004',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
       sd.id,
       (CURRENT_DATE + INTERVAL '7 days') + TIME '14:00',
       (CURRENT_DATE + INTERVAL '7 days') + TIME '16:00',
       'SCHEDULED', 1500.00, -300.00, 'Sadık müşteri indirimi', 1200.00, 'Keratin bakımını denemek istiyor', 0, NOW(), NOW()
FROM service_definition sd WHERE sd.name = 'Keratin Bakım';

-- ============================================================
-- 10. RANDEVU BAYRAKLARI
-- ============================================================
INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
SELECT a.id, 'VIP', 'VIP Müşteri', '⭐'
FROM appointment a WHERE a.customer_phone = '05551110006';

INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
SELECT a.id, 'ALLERGY', 'Lateks alerjisi var', '⚠️'
FROM appointment a WHERE a.customer_phone = '05551110008';

INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
SELECT a.id, 'DRINK', 'Çay', '☕'
FROM appointment a WHERE a.customer_phone = '05551110011';

INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
SELECT a.id, 'PREFERENCE', 'Düğün hazırlığı — özel ilgi', '💍'
FROM appointment a WHERE a.customer_phone = '05551110013';

INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
SELECT a.id, 'CUSTOM', 'Referans: Selin Arslan', '👤'
FROM appointment a WHERE a.customer_phone = '05551110015';

-- ============================================================
-- 11. ÖDEMELER (tamamlanan randevular için)
-- ============================================================

-- Selin — Saç Kesim (7 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Selin Arslan', '05551110001', 300.00, 'CARD', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110001' AND a.status = 'COMPLETED'
AND a.start_time::date = CURRENT_DATE - INTERVAL '7 days';

-- Büşra — Cilt Bakımı (7 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Büşra Aktaş', '05551110003', 450.00, 'CASH', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110003' AND a.status = 'COMPLETED'
AND a.start_time::date = CURRENT_DATE - INTERVAL '7 days';

-- Deniz — Manikür (7 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Deniz Yıldız', '05551110002', 250.00, 'CASH', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110002' AND a.status = 'COMPLETED'
AND a.start_time::date = CURRENT_DATE - INTERVAL '7 days';

-- Gamze — Lazer (5 gün önce) — VERESİYE
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, deferred_note, collected_at, staff_id, created_at)
SELECT a.id, 'Gamze Özkan', '05551110004', 600.00, 'CASH', 'DEFERRED',
       'Paket bitince toplu ödeme yapacak',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110004' AND a.status = 'COMPLETED';

-- Hülya — Saç Boyama (5 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Hülya Koç', '05551110005', 800.00, 'CARD', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110005' AND a.status = 'COMPLETED';

-- İrem — Alexandrit Lazer seans 1 (2 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'İrem Polat', '05551110006', 900.00, 'CARD', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110006' AND a.status = 'COMPLETED';

-- Esra — Keratin Bakım (1 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Esra Yılmaz', '05551110011', 1300.00, 'CASH', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110011' AND a.status = 'COMPLETED'
AND a.start_time::date = CURRENT_DATE - INTERVAL '1 day' AND a.base_price = 1500.00;

-- Melis — Pedikür (1 gün önce)
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Melis Aydın', '05551110008', 350.00, 'CARD', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110008' AND a.status = 'COMPLETED';

-- Bugün tamamlanan — Selin Fön
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Selin Arslan', '05551110001', 200.00, 'CASH', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110001' AND a.status = 'COMPLETED'
AND a.start_time::date = CURRENT_DATE;

-- Bugün tamamlanan — Pınar Cilt Bakımı
INSERT INTO payment (appointment_id, customer_name, customer_phone, amount, method, status, collected_at, staff_id, created_at)
SELECT a.id, 'Pınar Güneş', '05551110010', 500.00, 'CARD', 'PAID',
       a.end_time, (SELECT id FROM staff WHERE name = 'Merve Şahin'), a.end_time
FROM appointment a WHERE a.customer_phone = '05551110010' AND a.status = 'COMPLETED';

-- ============================================================
-- 12. BEKLEME LİSTESİ
-- ============================================================
INSERT INTO waitlist_entry (customer_name, customer_phone, service_id, preferred_staff_id, preferred_date, preferred_time, notes, fulfilled, created_at) VALUES
    ('Gamze Özkan', '05551110004',
     (SELECT id FROM service_definition WHERE name = 'Lazer Epilasyon'),
     (SELECT id FROM staff WHERE name = 'Fatma Demir'),
     CURRENT_DATE + INTERVAL '2 days', '14:00', 'Seans arası ek randevu istiyor', false, NOW() - INTERVAL '1 day'),

    ('Sibel Özer', '05551110012',
     (SELECT id FROM service_definition WHERE name = 'Manikür'),
     NULL,
     CURRENT_DATE + INTERVAL '3 days', '10:00', 'Geçen sefer gelmedi, tekrar denemek istiyor', false, NOW()),

    ('Cansu Erdoğan', '05551110007',
     (SELECT id FROM service_definition WHERE name = 'Cilt Bakımı'),
     (SELECT id FROM staff WHERE name = 'Elif Çelik'),
     CURRENT_DATE + INTERVAL '5 days', '15:00', NULL, false, NOW()),

    ('Selin Arslan', '05551110001',
     (SELECT id FROM service_definition WHERE name = 'Saç Boyama'),
     (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz'),
     CURRENT_DATE - INTERVAL '2 days', '11:00', 'Randevu açılırsa haber verin', true, NOW() - INTERVAL '5 days');

-- ============================================================
-- 13. MASRAFLAR (Expenses)
-- ============================================================
INSERT INTO expense (description, amount, category, expense_date, staff_id, notes, created_at) VALUES
    ('Aylık kira',                    15000.00, 'RENT',        CURRENT_DATE - INTERVAL '15 days', NULL, NULL, NOW() - INTERVAL '15 days'),
    ('Elektrik faturası',              2800.00, 'UTILITIES',   CURRENT_DATE - INTERVAL '12 days', NULL, 'Mayıs ayı', NOW() - INTERVAL '12 days'),
    ('Su faturası',                     450.00, 'UTILITIES',   CURRENT_DATE - INTERVAL '12 days', NULL, 'Mayıs ayı', NOW() - INTERVAL '12 days'),
    ('Saç boyası malzeme',             3200.00, 'SUPPLIES',    CURRENT_DATE - INTERVAL '10 days', NULL, 'LOreal Professional sipariş', NOW() - INTERVAL '10 days'),
    ('Lazer cihazı bakımı',           1500.00, 'MAINTENANCE', CURRENT_DATE - INTERVAL '8 days',  NULL, 'Yıllık bakım', NOW() - INTERVAL '8 days'),
    ('Temizlik malzemesi',              650.00, 'SUPPLIES',    CURRENT_DATE - INTERVAL '5 days',  NULL, NULL, NOW() - INTERVAL '5 days'),
    ('Boya ve tırnak malzemesi',       1800.00, 'SUPPLIES',    CURRENT_DATE - INTERVAL '3 days',  NULL, 'OPI + Keratin seti', NOW() - INTERVAL '3 days'),
    ('Klima servisi',                   800.00, 'MAINTENANCE', CURRENT_DATE - INTERVAL '2 days',  NULL, NULL, NOW() - INTERVAL '2 days'),
    ('İnternet faturası',               350.00, 'UTILITIES',   CURRENT_DATE - INTERVAL '1 day',   NULL, NULL, NOW() - INTERVAL '1 day');

-- ============================================================
-- 14. ÜRÜNLER (Products)
-- ============================================================
INSERT INTO product (name, category, price, stock_quantity, low_stock_threshold, active, created_at, updated_at) VALUES
    ('Keratin Şampuan 300ml',          'Saç Bakım',       185.00,  24, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Argan Yağı Serum 100ml',         'Saç Bakım',       250.00,  12, 3,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Saç Maskesi 250ml',              'Saç Bakım',       320.00,   8, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Cilt Temizleme Jeli 200ml',      'Cilt Bakım',      145.00,  18, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Nemlendirici Krem SPF30 50ml',   'Cilt Bakım',      280.00,   3, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Göz Çevresi Kremi 15ml',         'Cilt Bakım',      195.00,   6, 3,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Oje Seti (6lı)',                 'Tırnak',          120.00,  15, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Tırnak Güçlendirici',            'Tırnak',           85.00,   2, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Lazer Sonrası Krem 100ml',       'Lazer Bakım',     210.00,  10, 3,  true, NOW() - INTERVAL '60 days', NOW()),
    ('Güneş Koruyucu SPF50 75ml',      'Genel',           165.00,  20, 5,  true, NOW() - INTERVAL '60 days', NOW()),
    ('El Kremi 75ml (Parabensiz)',      'Genel',            95.00,   0, 5, false, NOW() - INTERVAL '60 days', NOW());

-- ============================================================
-- 15. ÜRÜN SATIŞLARI
-- ============================================================
INSERT INTO product_sale (product_id, quantity, unit_price, total_price, appointment_id, customer_phone, customer_name, sold_at, staff_id)
SELECT p.id, 1, 185.00, 185.00,
       (SELECT a.id FROM appointment a WHERE a.customer_phone = '05551110001' AND a.start_time::date = CURRENT_DATE - INTERVAL '7 days' LIMIT 1),
       '05551110001', 'Selin Arslan',
       (CURRENT_DATE - INTERVAL '7 days') + TIME '10:00',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz')
FROM product p WHERE p.name = 'Keratin Şampuan 300ml';

INSERT INTO product_sale (product_id, quantity, unit_price, total_price, appointment_id, customer_phone, customer_name, sold_at, staff_id)
SELECT p.id, 1, 280.00, 280.00,
       (SELECT a.id FROM appointment a WHERE a.customer_phone = '05551110003' AND a.status = 'COMPLETED' LIMIT 1),
       '05551110003', 'Büşra Aktaş',
       (CURRENT_DATE - INTERVAL '7 days') + TIME '11:30',
       (SELECT id FROM staff WHERE name = 'Fatma Demir')
FROM product p WHERE p.name = 'Nemlendirici Krem SPF30 50ml';

INSERT INTO product_sale (product_id, quantity, unit_price, total_price, appointment_id, customer_phone, customer_name, sold_at, staff_id)
SELECT p.id, 2, 210.00, 420.00,
       (SELECT a.id FROM appointment a WHERE a.customer_phone = '05551110006' AND a.status = 'COMPLETED' LIMIT 1),
       '05551110006', 'İrem Polat',
       (CURRENT_DATE - INTERVAL '2 days') + TIME '10:45',
       (SELECT id FROM staff WHERE name = 'Fatma Demir')
FROM product p WHERE p.name = 'Lazer Sonrası Krem 100ml';

INSERT INTO product_sale (product_id, quantity, unit_price, total_price, customer_phone, customer_name, sold_at, staff_id)
SELECT p.id, 1, 250.00, 250.00,
       '05551110011', 'Esra Yılmaz',
       (CURRENT_DATE - INTERVAL '1 day') + TIME '15:30',
       (SELECT id FROM staff WHERE name = 'Ayşe Yılmaz')
FROM product p WHERE p.name = 'Argan Yağı Serum 100ml';

-- ============================================================
-- 16. DENETİM GÜNLÜĞÜ (Audit Log) — Birkaç örnek kayıt
-- ============================================================
INSERT INTO audit_log_entry (user_id, entity_type, entity_id, action, old_value, new_value, created_at) VALUES
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 1, 'CREATE', NULL, 'Yeni randevu: Selin Arslan → Saç Kesim', NOW() - INTERVAL '7 days'),
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 2, 'CREATE', NULL, 'Yeni randevu: Büşra Aktaş → Cilt Bakımı', NOW() - INTERVAL '7 days'),
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 1, 'STATUS_CHANGE', 'SCHEDULED', 'COMPLETED', NOW() - INTERVAL '7 days' + INTERVAL '2 hours'),
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 6, 'STATUS_CHANGE', 'SCHEDULED', 'CANCELLED — Müşteri telefon ile iptal etti', NOW() - INTERVAL '3 days'),
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 7, 'STATUS_CHANGE', 'SCHEDULED', 'NO_SHOW', NOW() - INTERVAL '3 days'),
    ((SELECT id FROM users WHERE username = 'admin'), 'STAFF', 4, 'CREATE', NULL, 'Yeni personel: Elif Çelik', NOW() - INTERVAL '60 days'),
    ((SELECT id FROM users WHERE username = 'admin'), 'CUSTOMER', 1, 'CREATE', NULL, 'Yeni müşteri: Selin Arslan', NOW() - INTERVAL '80 days'),
    ((SELECT id FROM users WHERE username = 'merve'), 'APPOINTMENT', 9, 'UPDATE', 'base: 1500₺', 'final: 1300₺ (Paket indirimi -200₺)', NOW() - INTERVAL '1 day');
