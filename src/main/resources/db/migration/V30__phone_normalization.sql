-- V30: Telefonu kanonik biçime çevir — müşteri eşleştirmesi ham metne dayanmasın
--
-- Telefon bu üründe fiilî müşteri kimliği: randevu geçmişi, sadakat, kupon ve
-- aktivite kaydı hep customer_phone ham string'i üzerinden eşleşiyordu. Hiçbir
-- yerde normalizasyon olmadığı için "0532 111 22 33" ile "+905321112233" ayrı
-- müşteri sayılıyor, aynı kişi birden çok kayda bölünüyordu.
--
-- Ham kolonlar korunuyor: salon ne yazdıysa onu görmeye devam ediyor. Eşleştirme
-- yeni normalize kolonlar üzerinden yapılacak.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) Tek yetkili SQL normalizer
--
-- Kalıcı fonksiyon; backfill, yinelenen tespiti ve gelecekteki raporlar hep bunu
-- kullanır. Kurallar com.gscrm.util.PhoneNormalizer ile birebir aynıdır —
-- ayrışmayı PhoneNormalizerSqlParityIT testi engeller.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION gscrm_normalize_phone(raw text) RETURNS text AS $$
DECLARE
    d    text;
    nsn  text;
    intl boolean;
BEGIN
    IF raw IS NULL OR btrim(raw) = '' THEN
        RETURN NULL;
    END IF;

    intl := left(btrim(raw), 1) = '+';
    d    := regexp_replace(raw, '[^0-9]', '', 'g');

    IF left(d, 2) = '00' THEN
        intl := true;
        d    := substr(d, 3);
    END IF;

    IF intl THEN
        -- Ülke kodu 90 ise TR kuralları; yabancı numara uzunluğa göre kabul edilir.
        IF left(d, 2) = '90' THEN
            nsn := substr(d, 3);
            IF length(nsn) = 10 AND substr(nsn, 1, 1) BETWEEN '2' AND '5' THEN
                RETURN '+90' || nsn;
            END IF;
            RETURN NULL;
        END IF;
        IF length(d) BETWEEN 8 AND 15 THEN
            RETURN '+' || d;
        END IF;
        RETURN NULL;
    END IF;

    -- Ulusal yazımlar: 905321234567 / 0905321234567 / 05321234567 / 5321234567
    IF    length(d) = 12 AND left(d, 2) = '90'  THEN nsn := substr(d, 3);
    ELSIF length(d) = 13 AND left(d, 3) = '090' THEN nsn := substr(d, 4);
    ELSIF length(d) = 11 AND left(d, 1) = '0'   THEN nsn := substr(d, 2);
    ELSIF length(d) = 10                        THEN nsn := d;
    ELSE  RETURN NULL;
    END IF;

    IF length(nsn) = 10 AND substr(nsn, 1, 1) BETWEEN '2' AND '5' THEN
        RETURN '+90' || nsn;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) Kolonlar ve backfill
--
-- Çözümlenemeyen girdi NULL olur; eşleştirme sorguları null'da kısa devre yapar,
-- böylece çöp girdi iki yabancıyı birbirine bağlayamaz. Bu aynı zamanda bugünkü
-- '' (boş string) kovası hatasını da kapatır: telefonsuz randevular tek bir
-- müşterinin geçmişi gibi sayılıyordu.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE customer    ADD COLUMN IF NOT EXISTS phone_normalized          VARCHAR(32);
ALTER TABLE appointment ADD COLUMN IF NOT EXISTS customer_phone_normalized VARCHAR(32);

UPDATE customer    SET phone_normalized          = gscrm_normalize_phone(phone);
UPDATE appointment SET customer_phone_normalized = gscrm_normalize_phone(customer_phone);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3) Index'ler
--
-- uk_customer_salon_phone (V16) düşürülüyor: kimlik normalize değer olunca ham
-- kolon üzerindeki teklik kimsenin umursamadığı bir özelliği zorluyor ve asıl
-- kötüsü düzeltmeyi engelliyor — admin yinelenen uyarısını görüp bir satırı
-- kanonik forma çevirmek istediğinde index keyfî biçimde reddediyor.
-- phone_normalized üzerine unique koymak da mümkün değil: yinelenenler bilerek
-- birleştirilmiyor, salon sahibi elle karar verecek. Yerine geçen koruma
-- CustomerService.create/update içindeki 409 kontrolü — o normalizasyonu anlar.
-- ─────────────────────────────────────────────────────────────────────────────
DROP INDEX IF EXISTS uk_customer_salon_phone;

CREATE INDEX IF NOT EXISTS idx_customer_salon_phone_norm
    ON customer(salon_id, phone_normalized)
    WHERE phone_normalized IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_appointment_salon_phone_norm
    ON appointment(salon_id, customer_phone_normalized, start_time)
    WHERE customer_phone_normalized IS NOT NULL;
