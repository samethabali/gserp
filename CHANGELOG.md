# Changelog

## 1.1.0 — 2026-06-22

### SaaS & Faturalandırma
- Abonelik sayfası (`/settings/billing`) — plan, kota ve aylık WhatsApp kullanımı
- Trial bitince salt okunur mod (`SubscriptionReadOnlyFilter`, HTTP 402)
- Deneme uyarı banner'ı (7 gün kala) tüm yönetim sayfalarında
- WhatsApp kotası org bazlı sayaç (`SubscriptionService.incrementUsage`)

### Onboarding
- 5 adımlı kurulum sihirbazı (`/onboarding/setup`) — salon, hizmet, personel, WhatsApp
- Public kayıt sonrası kurulum yönlendirmesi

### Demo & Test
- `docs/DEMO.md`, dev kullanıcı şifreleri (`admin`)
- Demo özellikleri integration testleri (`DemoFeaturesIT`, `SubscriptionServiceTest`)

## 1.0.0 — 2026-06-16

### Güvenlik
- Rol tabanlı API ve sayfa erişimi (ADMIN / RECEPTIONIST / SPECIALIST / CUSTOMER)
- Uzman yalnızca kendi randevularını görür ve kısıtlı durum günceller
- Public booking rate limit
- Parola değiştirme ve ilk girişte zorunlu değişim

### Ürün
- Public booking `PENDING_APPROVAL` akışı
- Personel kullanıcı yönetimi (admin)
- KVKK onay checkbox ve gizlilik sayfası
- Salon white-label ayarları (`salon_settings`)
- WhatsApp bildirim altyapısı (onay, hatırlatma, iptal — API ile)

### Operasyon
- CI workflow (`mvn verify`)
- `scripts/backup-db.sh`
- Flyway V11–V13 migration'ları
