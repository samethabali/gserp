# GSCRM — Veri İşleme Sözleşmesi (DPA) Taslağı

## Taraflar

- **Veri Sorumlusu:** Salon işletmesi / Franchise merkezi (müşteri)
- **Veri İşleyen:** GSCRM platform operatörü

## İşleme Kapsamı

| Veri | Amaç | Hukuki Sebep |
|------|------|--------------|
| Ad, soyad, telefon, e-posta | Randevu, hatırlatma, hizmet | Sözleşme / meşru menfaat |
| Randevu geçmişi | Operasyon, raporlama | Sözleşme |
| Ödeme kayıtları | Tahsilat | Sözulşme |

## Alt İşleyenler

| Alt işleyen | Amaç | Konum |
|-------------|------|-------|
| VDS hosting sağlayıcı | Barındırma | TR/EU |

## Yükümlülükler (İşleyen)

- KVKK md. 12 güvenlik önlemleri
- İhlal bildirimi: 72 saat içinde sorumluya
- Veri silme/taşıma taleplerine teknik destek
- Denetim hakkı (yılda 1, önceden bildirimli)

## Yükümlülükler (Sorumlu)

- Aydınlatma metni ve rıza (booking/portal)
- Müşteri başvurularına yanıt (10 gün)

## Süre ve Fesih

- SaaS aboneliği süresince geçerli
- Fesih sonrası veri: 30 gün export penceresi, sonra silme

## Saklama Süreleri

Süreler `RetentionJob` tarafından her gece 03:30'da uygulanır.

| Veri | Süre | Uygulayan |
|------|------|-----------|
| İşlem kütüğü (`activity_event`) | 24 ay | `RetentionJob.purgeOldLogs()` |
| Eski denetim kaydı (`audit_log_entry`) | 90 gün | `RetentionJob.purgeOldLogs()` |
| Fesih sonrası müşteri verisi | 30 gün export penceresi, sonra silme | Manuel |

İşlem kütüğü giriş/çıkış, başarısız giriş denemeleri, reddedilen istekler,
kişisel veri görüntüleme (`VIEW` / `EXPORT`) ve değişikliklerin eski/yeni
değerlerini taşır. Kişisel veriler kütüğe maskeli yazılır (telefon ve e-posta
son 4 hane); parola hiçbir koşulda yazılmaz.

Tablo daha önce temizliğe hiç dahil değildi ve sınırsız büyüyordu.
