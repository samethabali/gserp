-- V8: Ürün maliyet fiyatı ve satış-müşteri ilişkisi iyileştirmesi

-- 1. Product tablosuna alış fiyatı (maliyet) ekle
ALTER TABLE product ADD COLUMN cost_price NUMERIC(12,2);

-- 2. ProductSale tablosuna customer_id FK ekle
ALTER TABLE product_sale ADD COLUMN customer_id BIGINT;
ALTER TABLE product_sale ADD CONSTRAINT fk_product_sale_customer
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL;
CREATE INDEX idx_product_sale_customer ON product_sale(customer_id);

-- 3. Mevcut ürünlere maliyet fiyatı ata (satış fiyatının ~%60'ı)
UPDATE product SET cost_price = ROUND(price * 0.55, 2) WHERE name = 'Keratin Şampuan 300ml';
UPDATE product SET cost_price = ROUND(price * 0.50, 2) WHERE name = 'Argan Yağı Serum 100ml';
UPDATE product SET cost_price = ROUND(price * 0.55, 2) WHERE name = 'Saç Maskesi 250ml';
UPDATE product SET cost_price = ROUND(price * 0.50, 2) WHERE name = 'Cilt Temizleme Jeli 200ml';
UPDATE product SET cost_price = ROUND(price * 0.45, 2) WHERE name = 'Nemlendirici Krem SPF30 50ml';
UPDATE product SET cost_price = ROUND(price * 0.50, 2) WHERE name = 'Göz Çevresi Kremi 15ml';
UPDATE product SET cost_price = ROUND(price * 0.60, 2) WHERE name = 'Oje Seti (6lı)';
UPDATE product SET cost_price = ROUND(price * 0.55, 2) WHERE name = 'Tırnak Güçlendirici';
UPDATE product SET cost_price = ROUND(price * 0.40, 2) WHERE name = 'Lazer Sonrası Krem 100ml';
UPDATE product SET cost_price = ROUND(price * 0.50, 2) WHERE name = 'Güneş Koruyucu SPF50 75ml';
UPDATE product SET cost_price = ROUND(price * 0.55, 2) WHERE name = 'El Kremi 75ml (Parabensiz)';

-- 4. Mevcut satışları müşteri kaydıyla eşleştir (phone üzerinden)
UPDATE product_sale ps
SET customer_id = c.id
FROM customer c
WHERE ps.customer_phone = c.phone AND ps.customer_phone IS NOT NULL;
