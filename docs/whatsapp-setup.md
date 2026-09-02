# Meta WhatsApp Cloud API — GSCRM Kurulum Rehberi

> **Ürün dışı (2026-08):** WhatsApp şu an GSCRM ürününde yok (UI, booking, otomatik gönderim kapalı).
> Bu dosya sonraki sürüm için teknik not olarak durur; canlı kurulum yapmayın.

Bu doküman kod dışı Meta Business kurulum adımlarını özetler. GSCRM Faz 2 hibrit modelde
WhatsApp yalnızca **onaylı şablon bildirimleri** için kullanılır (tam bot değil).

## 1. Önkoşullar

- Meta Business Portfolio (business.facebook.com)
- Doğrulanmış işletme
- WhatsApp Business telefon numarası (yeni veya migrate)
- HTTPS üzerinden erişilebilir GSCRM instance (`https://salon.domain`)

## 2. API erişimi

1. Meta Developers → Uygulama oluştur → WhatsApp ürününü ekle
2. **API Setup** sayfasından geçici token al (test için)
3. Kalıcı erişim için **System User** oluştur ve kalıcı token üret
4. `.env` dosyasına ekle (asla commit etme):

```env
WHATSAPP_ENABLED=true
WHATSAPP_TOKEN=<system_user_token>
WHATSAPP_PHONE_NUMBER_ID=<phone_number_id>
WHATSAPP_BUSINESS_ACCOUNT_ID=<waba_id>
WHATSAPP_SALON_PHONE=+905xxxxxxxxx
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<rastgele_güçlü_string>
```

## 3. Message templates

Meta onayı gerekir (genelde 1–24 saat). GSCRM şu şablon adlarını bekler:

| Şablon adı | Kategori | Kullanım |
|------------|----------|----------|
| `appointment_request_received` | utility | Randevu isteği alındı |
| `appointment_confirmed` | utility | Salon onayladı |
| `appointment_cancelled` | utility | İptal bildirimi |
| `appointment_reminder` | utility | 24 saat önce hatırlatma |

Her şablonda body parametreleri sırasıyla kodda `WhatsAppNotificationService` ile eşleşir.

## 4. Webhook

1. Meta panel → Webhook URL: `https://salon.domain/api/webhooks/whatsapp`
2. Verify token: `.env` içindeki `WHATSAPP_WEBHOOK_VERIFY_TOKEN` ile aynı
3. Abone olunan alanlar: `messages` (delivery status için opsiyonel)

GSCRM webhook controller GET ile doğrulama, POST ile delivery payload alır (loglanır).

## 5. wa.me fallback

`WHATSAPP_ENABLED=false` iken veya API henüz kurulmamışken public booking onay adımında
`WHATSAPP_SALON_PHONE` ile **WhatsApp'tan Yaz** butonu gösterilir.

## 6. Test

1. `WHATSAPP_ENABLED=true` yap, container restart
2. Public `/booking` üzerinden test randevu isteği gönder
3. `notification_log` tablosunda `SENT` / `FAILED` kayıtlarını kontrol et
4. Meta panel → Message insights

## 7. Maliyet notu

Meta konuşma başına ücretlendirir; randevu bildirimleri genelde **utility** kategorisindedir.
Güncel fiyatlandırma için Meta dokümantasyonuna bakın.
