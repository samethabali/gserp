# Changelog

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
