-- Personel kaydı başına en fazla bir giriş hesabı.
--
-- Personel eklendiğinde hesabı da otomatik açılıyor. Kural yalnızca uygulamada
-- dursaydı, eşzamanlı iki istek kontrolü birlikte geçip aynı personele iki hesap
-- yazabilir ve uzman kapsamı (staff_id) belirsiz hale gelirdi. Aynı indeks,
-- personel listesiyle birlikte hesap durumunu çeken sorguyu da karşılar.
CREATE UNIQUE INDEX uk_users_salon_staff
    ON users (salon_id, staff_id)
    WHERE staff_id IS NOT NULL;
