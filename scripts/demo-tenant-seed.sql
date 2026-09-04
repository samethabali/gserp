-- ============================================================================
-- GSERP — Demo / test kiracısı (salon slug: 'demo')
--
-- Ne yapar: 'demo' slug'lı bir organizasyon + salon + kullanıcılar açar ve o
-- salonu ~4 aylık gerçekçi işletme verisiyle doldurur (personel, kaynak,
-- hizmet, çalışma saatleri, müşteri, randevu, ödeme, ürün/stok, satış, gider,
-- kupon, sadakat kademesi, bekleme listesi).
--
-- Güvenlik: yalnızca 'demo' salonuna ve onun organizasyonuna ait satırlara
-- dokunur. Diğer kiracılar (ör. slug='default') hiç okunmaz, hiç yazılmaz.
--
-- Yeniden çalıştırılabilir: baştaki temizlik bloğu varsa eski demo verisini
-- siler, sonra her şeyi yeniden kurar. Demo hesabını sıfırlamak için scripti
-- yeniden çalıştırmak yeterlidir.
--
-- Çalıştırma (VPS):
--   docker exec -i postgres psql -U gserp_user -d gserp_db -v ON_ERROR_STOP=1 \
--     -f /tmp/demo-tenant-seed.sql
--
-- Giriş: demo / demo-resepsiyon / demo-uzman — parola Demo2026!
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 0) Var olan demo kiracısını temizle
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_salon bigint;
    v_org   bigint;
BEGIN
    SELECT id, organization_id INTO v_salon, v_org FROM salon WHERE slug = 'demo';
    IF v_salon IS NULL THEN
        RAISE NOTICE 'demo kiracisi yok — temizlik atlandi';
        RETURN;
    END IF;
    RAISE NOTICE 'Eski demo kiracisi siliniyor (salon_id=%, org_id=%)', v_salon, v_org;

    DELETE FROM appointment_body_region WHERE appointment_id IN (SELECT id FROM appointment WHERE salon_id = v_salon);
    DELETE FROM appointment_flag        WHERE appointment_id IN (SELECT id FROM appointment WHERE salon_id = v_salon);
    DELETE FROM appointment_resources   WHERE appointment_id IN (SELECT id FROM appointment WHERE salon_id = v_salon);
    DELETE FROM coupon_usage            WHERE salon_id = v_salon;
    DELETE FROM payment                 WHERE salon_id = v_salon;
    DELETE FROM product_sale            WHERE salon_id = v_salon;
    DELETE FROM appointment             WHERE salon_id = v_salon;
    DELETE FROM branch_stock            WHERE salon_id = v_salon;
    DELETE FROM product                 WHERE salon_id = v_salon;
    DELETE FROM expense                 WHERE salon_id = v_salon;
    DELETE FROM coupon                  WHERE salon_id = v_salon;
    DELETE FROM loyalty_tier            WHERE salon_id = v_salon;
    DELETE FROM waitlist_entry          WHERE salon_id = v_salon;
    DELETE FROM consent_record          WHERE salon_id = v_salon;
    DELETE FROM verification_code       WHERE salon_id = v_salon;
    DELETE FROM customer                WHERE salon_id = v_salon;
    DELETE FROM working_hours           WHERE salon_id = v_salon;
    DELETE FROM staff_specialization    WHERE staff_id IN (SELECT id FROM staff WHERE salon_id = v_salon);
    DELETE FROM service_required_resources WHERE service_id IN (SELECT id FROM service_definition WHERE salon_id = v_salon);
    DELETE FROM branch_service_price    WHERE salon_id = v_salon;
    DELETE FROM service_definition      WHERE salon_id = v_salon;
    DELETE FROM resource                WHERE salon_id = v_salon;
    DELETE FROM user_salon_role         WHERE salon_id = v_salon;
    DELETE FROM impersonation_log       WHERE salon_id = v_salon;
    DELETE FROM users                   WHERE salon_id = v_salon;
    DELETE FROM staff                   WHERE salon_id = v_salon;
    DELETE FROM sms_log                 WHERE salon_id = v_salon;
    DELETE FROM activity_event          WHERE salon_id = v_salon;
    DELETE FROM audit_log_entry         WHERE salon_id = v_salon;
    DELETE FROM branch_holiday          WHERE salon_id = v_salon;
    DELETE FROM salon_settings          WHERE salon_id = v_salon;
    DELETE FROM onboarding_state        WHERE salon_id = v_salon;
    DELETE FROM usage_meter             WHERE salon_id = v_salon;
    DELETE FROM salon                   WHERE id = v_salon;

    IF v_org IS NOT NULL AND NOT EXISTS (SELECT 1 FROM salon WHERE organization_id = v_org) THEN
        DELETE FROM billing_event             WHERE organization_id = v_org;
        DELETE FROM organization_subscription WHERE organization_id = v_org;
        DELETE FROM organization_owner        WHERE organization_id = v_org;
        DELETE FROM invite_redemption         WHERE organization_id = v_org;
        DELETE FROM usage_meter               WHERE organization_id = v_org;
        DELETE FROM organization              WHERE id = v_org;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 1) Kiracı iskeleti + mock veri
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_now   timestamp := date_trunc('minute', now());
    v_org   bigint;
    v_salon bigint;
    v_plan  bigint;

    -- personel
    s_ayse bigint; s_fatma bigint; s_zeynep bigint; s_elif bigint;
    s_resep bigint; s_mudur bigint;
    v_staff bigint[];

    -- kaynaklar
    r_sac1 bigint; r_sac2 bigint; r_cilt1 bigint; r_cilt2 bigint;
    r_lazer_oda bigint; r_lazer_cihaz bigint; r_ipl bigint;
    r_tirnak1 bigint; r_tirnak2 bigint;

    -- hizmetler
    sv_kesim bigint; sv_fon bigint; sv_boya bigint; sv_keratin bigint;
    sv_manikur bigint; sv_pedikur bigint; sv_protez bigint;
    sv_cilt bigint; sv_temizlik bigint; sv_hydra bigint;
    sv_lazer bigint; sv_alex bigint;

    -- müşteriler / ürünler
    c_ids bigint[]; c_names text[]; c_phones text[]; c_n int;
    p_ids bigint[]; p_prices numeric[]; p_n int;

    -- kuponlar
    k_hosgeldin bigint; k_yaz bigint;

    -- randevu döngüsü
    d            int;
    v_date       date;
    si           int;
    k            int;
    n_appt       int;
    base_slot    int;
    slot_ix      int;
    slot_hours   int[] := ARRAY[9, 11, 13, 15, 17];
    v_staff_id   bigint;
    v_pool_size  int;
    v_pos        int;
    v_service    bigint;
    v_res        bigint[];
    v_dur        int;
    v_price      numeric;
    v_cat        text;
    v_start      timestamp;
    v_end        timestamp;
    v_status     text;
    v_adj        numeric;
    v_appt       bigint;
    v_cix        int;
    v_cid        bigint;
    v_cname      text;
    v_cphone     text;
    v_r          int;
    v_rid        bigint;
    v_pay_method text;
    v_pay_status text;
    n_appt_total int := 0;
    n_pay_total  int := 0;
BEGIN
    ------------------------------------------------------------------
    -- Organizasyon + salon + abonelik
    ------------------------------------------------------------------
    INSERT INTO organization (name, type, active, created_at, loyalty_policy)
    VALUES ('Demo Güzellik Merkezi', 'STANDALONE', true, v_now - interval '120 days', 'SALON')
    RETURNING id INTO v_org;

    INSERT INTO salon (organization_id, slug, name, timezone, active, created_at, contact_email, dpo_name, showcase)
    VALUES (v_org, 'demo', 'Demo Güzellik Merkezi', 'Europe/Istanbul', true,
            v_now - interval '120 days', 'demo@gserp.avesitesi.xyz', 'Demo KVKK Sorumlusu', false)
    RETURNING id INTO v_salon;

    -- ACTIVE => salt-okunur moda hiç düşmez; demo hesabı süresiz yazılabilir kalır.
    SELECT id INTO v_plan FROM subscription_plan WHERE code = 'SOLO' AND active LIMIT 1;
    INSERT INTO organization_subscription (organization_id, plan_id, status, trial_end, current_period_end, created_at, updated_at)
    VALUES (v_org, v_plan, 'ACTIVE', NULL, v_now + interval '5 years', v_now - interval '120 days', v_now);

    INSERT INTO onboarding_state (salon_id, current_step, completed_at, updated_at)
    VALUES (v_salon, 'COMPLETED', v_now - interval '119 days', v_now);

    INSERT INTO salon_settings (salon_id, setting_key, setting_value, updated_at) VALUES
        (v_salon, 'salon.name',                       'Demo Güzellik Merkezi', v_now),
        (v_salon, 'salon.primary_color',              '#e91e8c',               v_now),
        (v_salon, 'salon.logo_url',                   '',                      v_now),
        (v_salon, 'booking.default_open',             '09:00',                 v_now),
        (v_salon, 'booking.default_close',            '19:00',                 v_now),
        (v_salon, 'booking.slot_step_minutes',        '15',                    v_now),
        (v_salon, 'booking.min_lead_minutes',         '120',                   v_now),
        (v_salon, 'booking.max_pending_per_phone',    '3',                     v_now),
        (v_salon, 'booking.sms_verification_enabled', 'false',                 v_now);

    ------------------------------------------------------------------
    -- Personel
    ------------------------------------------------------------------
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Ayşe Yılmaz', '05321000101', 'ayse@demo.gserp', 'SPECIALIST', '#e91e8c', true, v_now - interval '120 days', v_now)
    RETURNING id INTO s_ayse;
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Fatma Demir', '05321000102', 'fatma@demo.gserp', 'SPECIALIST', '#9b59b6', true, v_now - interval '120 days', v_now)
    RETURNING id INTO s_fatma;
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Zeynep Kaya', '05321000103', 'zeynep@demo.gserp', 'SPECIALIST', '#2196f3', true, v_now - interval '120 days', v_now)
    RETURNING id INTO s_zeynep;
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Elif Çelik', '05321000104', 'elif@demo.gserp', 'SPECIALIST', '#2ecc71', true, v_now - interval '95 days', v_now)
    RETURNING id INTO s_elif;
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Merve Şahin', '05321000105', 'merve@demo.gserp', 'RECEPTIONIST', '#ff9800', true, v_now - interval '120 days', v_now)
    RETURNING id INTO s_resep;
    INSERT INTO staff (salon_id, name, phone, email, role, color_hex, active, created_at, updated_at)
    VALUES (v_salon, 'Selin Aydın', '05321000106', 'selin@demo.gserp', 'ADMIN', '#607d8b', true, v_now - interval '120 days', v_now)
    RETURNING id INTO s_mudur;

    v_staff := ARRAY[s_ayse, s_fatma, s_zeynep, s_elif];

    INSERT INTO staff_specialization (staff_id, service_category) VALUES
        (s_ayse, 'HAIR'),
        (s_fatma, 'SKIN'), (s_fatma, 'LASER'),
        (s_zeynep, 'NAIL'),
        (s_elif, 'HAIR'), (s_elif, 'SKIN');

    ------------------------------------------------------------------
    -- Kullanıcılar — parola: Demo2026!  (bcrypt, strength 10)
    ------------------------------------------------------------------
    INSERT INTO users (salon_id, organization_id, username, password_hash, role, staff_id,
                       enabled, must_change_password, created_at, password_changed_at, token_version) VALUES
        (v_salon, v_org, 'demo',
         '$2a$10$s8Ka9SZOGK7ORutuWhD1M.tLzITB0/91wVclUFa4JWlvxcNCtIjj.',
         'ORG_OWNER', s_mudur, true, false, v_now - interval '120 days', v_now - interval '120 days', 0),
        (v_salon, v_org, 'demo-resepsiyon',
         '$2a$10$s8Ka9SZOGK7ORutuWhD1M.tLzITB0/91wVclUFa4JWlvxcNCtIjj.',
         'RECEPTIONIST', s_resep, true, false, v_now - interval '120 days', v_now - interval '120 days', 0),
        (v_salon, v_org, 'demo-uzman',
         '$2a$10$s8Ka9SZOGK7ORutuWhD1M.tLzITB0/91wVclUFa4JWlvxcNCtIjj.',
         'SPECIALIST', s_ayse, true, false, v_now - interval '120 days', v_now - interval '120 days', 0);

    ------------------------------------------------------------------
    -- Kaynaklar
    ------------------------------------------------------------------
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Saç Bakım Odası 1', 'ROOM', 1, true, v_now - interval '120 days') RETURNING id INTO r_sac1;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Saç Bakım Odası 2', 'ROOM', 1, true, v_now - interval '120 days') RETURNING id INTO r_sac2;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Cilt Bakım Odası 1', 'ROOM', 1, true, v_now - interval '120 days') RETURNING id INTO r_cilt1;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Cilt Bakım Odası 2', 'ROOM', 1, true, v_now - interval '120 days') RETURNING id INTO r_cilt2;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Lazer Odası', 'ROOM', 1, true, v_now - interval '120 days') RETURNING id INTO r_lazer_oda;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Lazer Cihazı', 'DEVICE', 1, true, v_now - interval '120 days') RETURNING id INTO r_lazer_cihaz;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'IPL Cihazı', 'DEVICE', 1, true, v_now - interval '90 days') RETURNING id INTO r_ipl;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Tırnak Seti 1', 'EQUIPMENT', 1, true, v_now - interval '120 days') RETURNING id INTO r_tirnak1;
    INSERT INTO resource (salon_id, name, resource_type, capacity, active, created_at)
    VALUES (v_salon, 'Tırnak Seti 2', 'EQUIPMENT', 1, true, v_now - interval '120 days') RETURNING id INTO r_tirnak2;

    ------------------------------------------------------------------
    -- Hizmetler
    ------------------------------------------------------------------
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Saç Kesim', 45, 350.00, 'HAIR', false, true, v_now - interval '120 days') RETURNING id INTO sv_kesim;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Fön', 30, 250.00, 'HAIR', false, true, v_now - interval '120 days') RETURNING id INTO sv_fon;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Saç Boyama', 90, 950.00, 'HAIR', true, true, v_now - interval '120 days') RETURNING id INTO sv_boya;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Keratin Bakım', 120, 1800.00, 'HAIR', true, true, v_now - interval '120 days') RETURNING id INTO sv_keratin;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Manikür', 45, 300.00, 'NAIL', true, true, v_now - interval '120 days') RETURNING id INTO sv_manikur;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Pedikür', 60, 400.00, 'NAIL', true, true, v_now - interval '120 days') RETURNING id INTO sv_pedikur;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Protez Tırnak', 90, 750.00, 'NAIL', true, true, v_now - interval '120 days') RETURNING id INTO sv_protez;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Cilt Bakımı', 60, 650.00, 'SKIN', true, true, v_now - interval '120 days') RETURNING id INTO sv_cilt;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Cilt Temizliği', 45, 500.00, 'SKIN', true, true, v_now - interval '120 days') RETURNING id INTO sv_temizlik;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Hydrafacial', 75, 1200.00, 'SKIN', true, true, v_now - interval '90 days') RETURNING id INTO sv_hydra;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Lazer Epilasyon', 30, 700.00, 'LASER', true, true, v_now - interval '120 days') RETURNING id INTO sv_lazer;
    INSERT INTO service_definition (salon_id, name, duration_minutes, base_price, category, requires_resource, active, created_at)
    VALUES (v_salon, 'Alexandrit Lazer', 45, 900.00, 'LASER', true, true, v_now - interval '90 days') RETURNING id INTO sv_alex;

    -- Hizmet → gerekli kaynak (aynı hizmet için birden çok satır = alternatifler)
    INSERT INTO service_required_resources (service_id, resource_id) VALUES
        (sv_boya, r_sac1), (sv_boya, r_sac2),
        (sv_keratin, r_sac1), (sv_keratin, r_sac2),
        (sv_manikur, r_tirnak1), (sv_manikur, r_tirnak2),
        (sv_pedikur, r_tirnak1), (sv_pedikur, r_tirnak2),
        (sv_protez, r_tirnak1), (sv_protez, r_tirnak2),
        (sv_cilt, r_cilt1), (sv_cilt, r_cilt2),
        (sv_temizlik, r_cilt1), (sv_temizlik, r_cilt2),
        (sv_hydra, r_cilt1), (sv_hydra, r_cilt2),
        (sv_lazer, r_lazer_oda), (sv_lazer, r_lazer_cihaz),
        (sv_alex, r_lazer_oda), (sv_alex, r_ipl);

    ------------------------------------------------------------------
    -- Çalışma saatleri — 4 uzman, Pzt-Cum 09-19, Cmt 10-17, Pazar izinli
    ------------------------------------------------------------------
    INSERT INTO working_hours (salon_id, staff_id, day_of_week, start_time, end_time, day_off)
    SELECT v_salon, st, dow,
           CASE WHEN dow = 'SATURDAY' THEN time '10:00' ELSE time '09:00' END,
           CASE WHEN dow = 'SATURDAY' THEN time '17:00' ELSE time '19:00' END,
           dow = 'SUNDAY'
    FROM unnest(v_staff) AS st,
         unnest(ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']) AS dow;

    ------------------------------------------------------------------
    -- Sadakat kademeleri + kuponlar
    ------------------------------------------------------------------
    INSERT INTO loyalty_tier (salon_id, name, min_completed, discount_percentage, active) VALUES
        (v_salon, 'Bronz',  5,  5.00, true),
        (v_salon, 'Gümüş', 12, 10.00, true),
        (v_salon, 'Altın',  25, 15.00, true);

    INSERT INTO coupon (salon_id, organization_id, scope, code, description, discount_type, discount_value,
                        min_appointments, valid_from, valid_until, max_uses, used_count, active, created_at)
    VALUES (v_salon, v_org, 'SALON', 'HOSGELDIN20', 'Yeni müşteriye %20 indirim', 'PERCENTAGE', 20,
            0, v_now - interval '90 days', v_now + interval '180 days', 200, 0, true, v_now - interval '90 days')
    RETURNING id INTO k_hosgeldin;

    INSERT INTO coupon (salon_id, organization_id, scope, code, description, discount_type, discount_value,
                        min_appointments, valid_from, valid_until, max_uses, used_count, active, created_at)
    VALUES (v_salon, v_org, 'SALON', 'YAZ150', 'Yaz kampanyası — 150 TL indirim', 'FIXED', 150,
            3, v_now - interval '45 days', v_now + interval '45 days', 100, 0, true, v_now - interval '45 days')
    RETURNING id INTO k_yaz;

    ------------------------------------------------------------------
    -- Müşteriler
    ------------------------------------------------------------------
    WITH ins AS (
        INSERT INTO customer (salon_id, home_salon_id, first_name, last_name, phone, phone_normalized,
                              email, notes, balance, consent_at, created_at, updated_at)
        VALUES
            (v_salon, v_salon, 'Merve',   'Aksoy',  '05331110001', gscrm_normalize_phone('05331110001'), 'merve.aksoy@ornek.com',   'VIP müşteri, Ayşe hanımı tercih ediyor',    0, v_now - interval '110 days', v_now - interval '110 days', v_now),
            (v_salon, v_salon, 'Selin',   'Yıldız', '05331110002', gscrm_normalize_phone('05331110002'), 'selin.yildiz@ornek.com',  'Boya alerjisi var — patch test şart', -250.00, v_now - interval '108 days', v_now - interval '108 days', v_now),
            (v_salon, v_salon, 'Deniz',   'Öztürk', '05331110003', gscrm_normalize_phone('05331110003'), 'deniz.ozturk@ornek.com',  '',                                          0, v_now - interval '100 days', v_now - interval '100 days', v_now),
            (v_salon, v_salon, 'Ceren',   'Arslan', '05331110004', gscrm_normalize_phone('05331110004'), 'ceren.arslan@ornek.com',  'Kahvesi sade',                              0, v_now - interval '95 days',  v_now - interval '95 days',  v_now),
            (v_salon, v_salon, 'Büşra',   'Koç',    '05331110005', gscrm_normalize_phone('05331110005'), 'busra.koc@ornek.com',     'Zeynep hanımla çalışıyor',                  0, v_now - interval '92 days',  v_now - interval '92 days',  v_now),
            (v_salon, v_salon, 'Aylin',   'Şahin',  '05331110006', gscrm_normalize_phone('05331110006'), 'aylin.sahin@ornek.com',   '',                                     150.00, v_now - interval '88 days',  v_now - interval '88 days',  v_now),
            (v_salon, v_salon, 'Nur',     'Yılmaz', '05331110007', gscrm_normalize_phone('05331110007'), 'nur.yilmaz@ornek.com',    'Hamile — kimyasal işlem yok',               0, v_now - interval '80 days',  v_now - interval '80 days',  v_now),
            (v_salon, v_salon, 'Pınar',   'Kara',   '05331110008', gscrm_normalize_phone('05331110008'), 'pinar.kara@ornek.com',    '',                                          0, v_now - interval '76 days',  v_now - interval '76 days',  v_now),
            (v_salon, v_salon, 'Ebru',    'Doğan',  '05331110009', gscrm_normalize_phone('05331110009'), 'ebru.dogan@ornek.com',    'Randevusunu sık erteliyor',                 0, v_now - interval '70 days',  v_now - interval '70 days',  v_now),
            (v_salon, v_salon, 'Gizem',   'Erdem',  '05331110010', gscrm_normalize_phone('05331110010'), 'gizem.erdem@ornek.com',   'VIP müşteri',                               0, v_now - interval '65 days',  v_now - interval '65 days',  v_now),
            (v_salon, v_salon, 'Sevgi',   'Aydın',  '05331110011', gscrm_normalize_phone('05331110011'), 'sevgi.aydin@ornek.com',   '',                                    -400.00, v_now - interval '60 days',  v_now - interval '60 days',  v_now),
            (v_salon, v_salon, 'Tuğçe',   'Polat',  '05331110012', gscrm_normalize_phone('05331110012'), 'tugce.polat@ornek.com',   'Lazer paketi — 8 seans',                    0, v_now - interval '55 days',  v_now - interval '55 days',  v_now),
            (v_salon, v_salon, 'Melis',   'Güneş',  '05331110013', gscrm_normalize_phone('05331110013'), 'melis.gunes@ornek.com',   '',                                          0, v_now - interval '50 days',  v_now - interval '50 days',  v_now),
            (v_salon, v_salon, 'İrem',    'Bulut',  '05331110014', gscrm_normalize_phone('05331110014'), 'irem.bulut@ornek.com',    'Hassas cilt',                               0, v_now - interval '45 days',  v_now - interval '45 days',  v_now),
            (v_salon, v_salon, 'Damla',   'Kurt',   '05331110015', gscrm_normalize_phone('05331110015'), 'damla.kurt@ornek.com',    '',                                      75.00, v_now - interval '40 days',  v_now - interval '40 days',  v_now),
            (v_salon, v_salon, 'Esra',    'Acar',   '05331110016', gscrm_normalize_phone('05331110016'), 'esra.acar@ornek.com',     '',                                          0, v_now - interval '35 days',  v_now - interval '35 days',  v_now),
            (v_salon, v_salon, 'Yasemin', 'Toprak', '05331110017', gscrm_normalize_phone('05331110017'), 'yasemin.toprak@ornek.com','Nikel alerjisi',                            0, v_now - interval '28 days',  v_now - interval '28 days',  v_now),
            (v_salon, v_salon, 'Simge',   'Ateş',   '05331110018', gscrm_normalize_phone('05331110018'), 'simge.ates@ornek.com',    '',                                          0, v_now - interval '21 days',  v_now - interval '21 days',  v_now),
            (v_salon, v_salon, 'Duygu',   'Tekin',  '05331110019', gscrm_normalize_phone('05331110019'), 'duygu.tekin@ornek.com',   'Instagram üzerinden geldi',                 0, v_now - interval '14 days',  v_now - interval '14 days',  v_now),
            (v_salon, v_salon, 'Ayça',    'Sezer',  '05331110020', gscrm_normalize_phone('05331110020'), 'ayca.sezer@ornek.com',    'Yeni müşteri',                              0, v_now - interval '5 days',   v_now - interval '5 days',   v_now)
        RETURNING id, first_name, last_name, phone
    )
    SELECT array_agg(id ORDER BY id),
           array_agg(first_name || ' ' || last_name ORDER BY id),
           array_agg(phone ORDER BY id)
    INTO c_ids, c_names, c_phones
    FROM ins;
    c_n := array_length(c_ids, 1);

    -- KVKK rızaları
    INSERT INTO consent_record (customer_id, salon_id, consent_type, version, granted_at)
    SELECT id, v_salon, 'PRIVACY', '1.0', created_at FROM customer WHERE salon_id = v_salon;
    INSERT INTO consent_record (customer_id, salon_id, consent_type, version, granted_at)
    SELECT id, v_salon, 'REMINDER', '1.0', created_at FROM customer WHERE salon_id = v_salon;
    INSERT INTO consent_record (customer_id, salon_id, consent_type, version, granted_at)
    SELECT id, v_salon, 'MARKETING', '1.0', created_at FROM customer WHERE salon_id = v_salon AND (id % 2) = 0;

    ------------------------------------------------------------------
    -- Ürünler + şube stoğu
    ------------------------------------------------------------------
    WITH ins AS (
        INSERT INTO product (salon_id, name, category, price, cost_price, stock_quantity, low_stock_threshold, active, created_at, updated_at)
        VALUES
            (v_salon, 'Onarıcı Şampuan 400ml', 'Saç Bakım',   450.00, 260.00, 24, 6, true, v_now - interval '120 days', v_now),
            (v_salon, 'Saç Maskesi 250ml',     'Saç Bakım',   620.00, 350.00, 15, 5, true, v_now - interval '120 days', v_now),
            (v_salon, 'Argan Saç Serumu',      'Saç Bakım',   780.00, 430.00,  4, 5, true, v_now - interval '110 days', v_now),
            (v_salon, 'Nemlendirici Krem',     'Cilt Bakım',  890.00, 500.00, 18, 5, true, v_now - interval '110 days', v_now),
            (v_salon, 'C Vitamini Serum',      'Cilt Bakım', 1250.00, 700.00,  9, 4, true, v_now - interval '90 days',  v_now),
            (v_salon, 'Güneş Koruyucu SPF50',  'Cilt Bakım',  680.00, 380.00,  2, 4, true, v_now - interval '80 days',  v_now),
            (v_salon, 'Tırnak Güçlendirici',   'Tırnak',      320.00, 180.00, 30, 8, true, v_now - interval '120 days', v_now),
            (v_salon, 'Kütikül Yağı',          'Tırnak',      240.00, 130.00, 21, 8, true, v_now - interval '120 days', v_now)
        RETURNING id, price
    )
    SELECT array_agg(id ORDER BY id), array_agg(price ORDER BY id) INTO p_ids, p_prices FROM ins;
    p_n := array_length(p_ids, 1);

    INSERT INTO branch_stock (salon_id, product_id, quantity, updated_at)
    SELECT v_salon, id, stock_quantity, v_now FROM product WHERE salon_id = v_salon;

    ------------------------------------------------------------------
    -- Randevular (son 120 gün + önümüzdeki 14 gün) + ödemeler
    ------------------------------------------------------------------
    CREATE TEMP TABLE tmp_demo_pool (staff_id bigint, pos int, service_id bigint, res bigint[]) ON COMMIT DROP;
    -- Her uzmanın kaynakları kendine özel: aynı uzmanın randevuları çakışmadığı
    -- için hiçbir oda/cihaz iki randevuya aynı anda düşmez.
    INSERT INTO tmp_demo_pool VALUES
        (s_ayse,   0, sv_kesim,    NULL),
        (s_ayse,   1, sv_fon,      NULL),
        (s_ayse,   2, sv_boya,     ARRAY[r_sac1]),
        (s_ayse,   3, sv_keratin,  ARRAY[r_sac1]),
        (s_fatma,  0, sv_cilt,     ARRAY[r_cilt1]),
        (s_fatma,  1, sv_hydra,    ARRAY[r_cilt1]),
        (s_fatma,  2, sv_lazer,    ARRAY[r_lazer_oda, r_lazer_cihaz]),
        (s_fatma,  3, sv_alex,     ARRAY[r_lazer_oda, r_ipl]),
        (s_zeynep, 0, sv_manikur,  ARRAY[r_tirnak1]),
        (s_zeynep, 1, sv_pedikur,  ARRAY[r_tirnak1]),
        (s_zeynep, 2, sv_protez,   ARRAY[r_tirnak1]),
        (s_elif,   0, sv_kesim,    NULL),
        (s_elif,   1, sv_fon,      NULL),
        (s_elif,   2, sv_boya,     ARRAY[r_sac2]),
        (s_elif,   3, sv_temizlik, ARRAY[r_cilt2]);

    FOR d IN -120..14 LOOP
        v_date := current_date + d;
        CONTINUE WHEN extract(dow from v_date) = 0;          -- Pazar kapalı

        FOR si IN 1..4 LOOP
            v_staff_id := v_staff[si];
            CONTINUE WHEN si = 4 AND v_date < current_date - 95;  -- Elif 95 gün önce başladı

            SELECT count(*) INTO v_pool_size FROM tmp_demo_pool WHERE staff_id = v_staff_id;
            n_appt    := 2 + ((abs(d) + si) % 3);            -- günde 2-4 randevu
            base_slot := 1 + ((abs(d) * 3 + si) % 2);        -- 09:00 ya da 11:00'dan başla

            FOR k IN 1..n_appt LOOP
                slot_ix := base_slot + k - 1;
                CONTINUE WHEN slot_ix > 5;

                v_pos := (abs(d) * 5 + si * 3 + k) % v_pool_size;
                SELECT service_id, res INTO v_service, v_res
                FROM tmp_demo_pool WHERE staff_id = v_staff_id AND pos = v_pos;

                SELECT duration_minutes, base_price, category INTO v_dur, v_price, v_cat
                FROM service_definition WHERE id = v_service;

                v_start := v_date + (slot_hours[slot_ix] || ' hours')::interval;
                v_end   := v_start + (v_dur || ' minutes')::interval;

                v_cix    := 1 + ((abs(d) * 7 + si * 2 + k * 5) % c_n);
                v_cid    := c_ids[v_cix];
                v_cname  := c_names[v_cix];
                v_cphone := c_phones[v_cix];

                v_r := (abs(d) * 11 + si * 5 + k * 3) % 12;
                IF v_start < v_now THEN
                    v_status := CASE WHEN v_r < 10 THEN 'COMPLETED'
                                     WHEN v_r = 10 THEN 'CANCELLED'
                                     ELSE 'NO_SHOW' END;
                ELSE
                    v_status := CASE WHEN v_r = 0 THEN 'PENDING_APPROVAL' ELSE 'SCHEDULED' END;
                END IF;

                v_adj := CASE WHEN v_r = 3 THEN -50.00 WHEN v_r = 7 THEN -100.00 ELSE 0 END;

                INSERT INTO appointment (salon_id, customer_name, customer_phone, customer_phone_normalized,
                                         staff_id, service_id, start_time, end_time, status,
                                         base_price, adjustment, adjustment_note, final_price,
                                         internal_note, cancellation_reason, version, created_at, updated_at)
                VALUES (v_salon, v_cname, v_cphone, gscrm_normalize_phone(v_cphone),
                        v_staff_id, v_service, v_start, v_end, v_status,
                        v_price, v_adj,
                        CASE WHEN v_adj < 0 THEN 'Sadakat indirimi' ELSE '' END,
                        v_price + v_adj,
                        CASE WHEN v_r = 5 THEN 'Müşteri kahve istedi' ELSE '' END,
                        CASE WHEN v_status = 'CANCELLED' THEN 'Müşteri iptal etti' ELSE NULL END,
                        0, v_start - interval '3 days', v_start - interval '3 days')
                RETURNING id INTO v_appt;
                n_appt_total := n_appt_total + 1;

                IF v_res IS NOT NULL THEN
                    FOREACH v_rid IN ARRAY v_res LOOP
                        INSERT INTO appointment_resources (appointment_id, resource_id) VALUES (v_appt, v_rid);
                    END LOOP;
                END IF;

                IF v_cat = 'LASER' THEN
                    INSERT INTO appointment_body_region (appointment_id, region)
                    VALUES (v_appt, (ARRAY['UNDERARM','BIKINI','LOWER_LEG','UPPER_LIP'])[1 + (v_r % 4)])
                    ON CONFLICT DO NOTHING;
                END IF;

                IF v_cix IN (1, 10) THEN
                    INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
                    VALUES (v_appt, 'VIP', 'VIP müşteri', '⭐');
                END IF;
                IF v_cix = 2 THEN
                    INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
                    VALUES (v_appt, 'ALLERGY', 'Boya alerjisi — patch test yapılmalı', '⚠️');
                END IF;
                IF v_r = 5 THEN
                    INSERT INTO appointment_flag (appointment_id, flag_type, flag_value, icon)
                    VALUES (v_appt, 'DRINK', 'Türk kahvesi, orta', '☕');
                END IF;

                IF v_status = 'COMPLETED' THEN
                    v_pay_method := CASE WHEN (v_r % 2) = 0 THEN 'CARD' ELSE 'CASH' END;
                    v_pay_status := CASE WHEN v_r = 9 THEN 'DEFERRED' ELSE 'PAID' END;
                    INSERT INTO payment (salon_id, appointment_id, customer_name, customer_phone, amount,
                                         method, status, deferred_note, collected_at, staff_id, created_at)
                    VALUES (v_salon, v_appt, v_cname, v_cphone, v_price + v_adj,
                            v_pay_method, v_pay_status,
                            CASE WHEN v_pay_status = 'DEFERRED' THEN 'Sonraki ziyarette tahsil edilecek' ELSE NULL END,
                            CASE WHEN v_pay_status = 'DEFERRED' THEN NULL ELSE v_end END,
                            v_staff_id, v_end);
                    n_pay_total := n_pay_total + 1;

                    IF v_r = 4 THEN
                        INSERT INTO coupon_usage (salon_id, coupon_id, customer_id, appointment_id, used_at)
                        VALUES (v_salon, k_hosgeldin, v_cid, v_appt, v_end);
                        UPDATE coupon SET used_count = used_count + 1 WHERE id = k_hosgeldin;
                    END IF;
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;

    ------------------------------------------------------------------
    -- Ürün satışları (son 90 gün)
    ------------------------------------------------------------------
    FOR d IN 1..90 LOOP
        CONTINUE WHEN (d % 3) <> 0;
        v_date := current_date - d;
        CONTINUE WHEN extract(dow from v_date) = 0;

        FOR k IN 1..(1 + (d % 2)) LOOP
            v_pos := 1 + ((d * 5 + k * 3) % p_n);
            v_cix := 1 + ((d * 7 + k) % c_n);
            v_r   := 1 + ((d + k) % 2);
            INSERT INTO product_sale (salon_id, product_id, quantity, unit_price, total_price,
                                      customer_id, customer_name, customer_phone, staff_id, sold_at)
            VALUES (v_salon, p_ids[v_pos], v_r, p_prices[v_pos], p_prices[v_pos] * v_r,
                    c_ids[v_cix], c_names[v_cix], c_phones[v_cix],
                    v_staff[1 + ((d + k) % 4)],
                    v_date + interval '15 hours' + ((k * 37) || ' minutes')::interval);
        END LOOP;
    END LOOP;

    ------------------------------------------------------------------
    -- Giderler (son 4 ay)
    ------------------------------------------------------------------
    FOR d IN 0..3 LOOP
        v_date := date_trunc('month', current_date - (d || ' months')::interval)::date;
        INSERT INTO expense (salon_id, description, amount, category, expense_date, staff_id, notes, created_at) VALUES
            (v_salon, 'Dükkân kirası',           45000.00, 'RENT',        LEAST(v_date + 1,  current_date), NULL,     'Aylık kira',                   LEAST(v_date + 1,  current_date)),
            (v_salon, 'Elektrik + su',            6800.00, 'UTILITIES',   LEAST(v_date + 5,  current_date), NULL,     '',                             LEAST(v_date + 5,  current_date)),
            (v_salon, 'İnternet + telefon',       1450.00, 'UTILITIES',   LEAST(v_date + 5,  current_date), NULL,     '',                             LEAST(v_date + 5,  current_date)),
            (v_salon, 'Boya ve bakım ürünleri',  18500.00, 'SUPPLIES',    LEAST(v_date + 8,  current_date), NULL,     'Toptancı siparişi',            LEAST(v_date + 8,  current_date)),
            (v_salon, 'Tek kullanımlık malzeme',  4200.00, 'SUPPLIES',    LEAST(v_date + 15, current_date), NULL,     '',                             LEAST(v_date + 15, current_date)),
            (v_salon, 'Maaş — Ayşe Yılmaz',      32000.00, 'SALARY',      LEAST(v_date + 28, current_date), s_ayse,   '',                             LEAST(v_date + 28, current_date)),
            (v_salon, 'Maaş — Fatma Demir',      30000.00, 'SALARY',      LEAST(v_date + 28, current_date), s_fatma,  '',                             LEAST(v_date + 28, current_date)),
            (v_salon, 'Maaş — Zeynep Kaya',      28000.00, 'SALARY',      LEAST(v_date + 28, current_date), s_zeynep, '',                             LEAST(v_date + 28, current_date)),
            (v_salon, 'Maaş — Merve Şahin',      26000.00, 'SALARY',      LEAST(v_date + 28, current_date), s_resep,  '',                             LEAST(v_date + 28, current_date)),
            (v_salon, 'Cihaz bakımı',             3500.00, 'MAINTENANCE', LEAST(v_date + 20, current_date), NULL,     'Lazer cihazı periyodik bakım', LEAST(v_date + 20, current_date));
    END LOOP;
    INSERT INTO expense (salon_id, description, amount, category, expense_date, staff_id, notes, created_at) VALUES
        (v_salon, 'Sosyal medya reklamı', 5000.00, 'OTHER', current_date - 12, NULL, 'Instagram kampanyası', v_now - interval '12 days'),
        (v_salon, 'Muhasebe hizmeti',     4500.00, 'OTHER', current_date - 30, NULL, '',                     v_now - interval '30 days');

    ------------------------------------------------------------------
    -- Bekleme listesi
    ------------------------------------------------------------------
    INSERT INTO waitlist_entry (salon_id, customer_name, customer_phone, service_id, preferred_staff_id,
                                preferred_date, preferred_time, notes, fulfilled, created_at) VALUES
        (v_salon, c_names[3],  c_phones[3],  sv_boya,    s_ayse,   current_date + 2, time '14:00', 'Cumartesi de olur',      false, v_now - interval '2 days'),
        (v_salon, c_names[9],  c_phones[9],  sv_hydra,   s_fatma,  current_date + 3, time '11:00', 'İlk boşlukta arayın',    false, v_now - interval '1 day'),
        (v_salon, c_names[15], c_phones[15], sv_protez,  s_zeynep, current_date + 5, time '16:00', '',                       false, v_now - interval '6 hours'),
        (v_salon, c_names[6],  c_phones[6],  sv_keratin, NULL,     current_date - 4, time '10:00', 'Randevuya dönüştürüldü', true,  v_now - interval '8 days');

    RAISE NOTICE 'Demo kiracisi hazir: org_id=%, salon_id=%, % randevu, % odeme',
                 v_org, v_salon, n_appt_total, n_pay_total;
END $$;

COMMIT;

-- ---------------------------------------------------------------------------
-- Özet
-- ---------------------------------------------------------------------------
\echo ''
\echo '=== DEMO KIRACISI OZETI ==='
SELECT s.id AS salon_id, s.slug, o.id AS org_id,
       (SELECT count(*) FROM staff              WHERE salon_id = s.id) AS personel,
       (SELECT count(*) FROM service_definition WHERE salon_id = s.id) AS hizmet,
       (SELECT count(*) FROM resource           WHERE salon_id = s.id) AS kaynak,
       (SELECT count(*) FROM customer           WHERE salon_id = s.id) AS musteri,
       (SELECT count(*) FROM appointment        WHERE salon_id = s.id) AS randevu,
       (SELECT count(*) FROM payment            WHERE salon_id = s.id) AS odeme,
       (SELECT count(*) FROM product_sale       WHERE salon_id = s.id) AS urun_satisi,
       (SELECT count(*) FROM expense            WHERE salon_id = s.id) AS gider
FROM salon s JOIN organization o ON o.id = s.organization_id
WHERE s.slug = 'demo';

SELECT username, role, enabled, must_change_password FROM users
WHERE salon_id = (SELECT id FROM salon WHERE slug = 'demo') ORDER BY id;

SELECT status, count(*) FROM appointment
WHERE salon_id = (SELECT id FROM salon WHERE slug = 'demo') GROUP BY status ORDER BY 1;
