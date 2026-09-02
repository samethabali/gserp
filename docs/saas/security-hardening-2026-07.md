# GSCRM — Güvenlik Sertleştirme Güncellemesi (2026-07)

**Branch:** `production-ready`
**Kapsam:** Ürünleştirme öncesi P0 güvenlik blocker'ları + P1 sağlamlaştırma
**Durum:** ✅ Tamamlandı — `mvn verify` yeşil (51 test, 0 hata)

Bu güncelleme, kapsamlı kod analizinde tespit edilen kritik güvenlik açıklarını
kapatır ve kota sayımını ölçeklenebilir hale getirir. Aşağıdaki her madde
kod + test ile doğrulanmıştır.

---

## P0 — Kritik güvenlik (launch blocker)

### P0.1 — Cross-tenant erişim engeli (`TenantAccessFilter`)

**Açık:** Tenant, tamamen istemci kontrolündeki `X-Salon-Slug` header/cookie'sinden
çözümleniyordu. Hiçbir katman, oturumdaki kullanıcının `salonId`'sinin çözümlenen
tenant ile eşleştiğini doğrulamıyordu. A salonu resepsiyonisti, `X-Salon-Slug: B`
göndererek B salonunun verisine erişebiliyordu (yatay yetki yükseltme).

**Çözüm:** [TenantAccessFilter.java](../../src/main/java/com/gscrm/security/TenantAccessFilter.java)
— `JwtAuthenticationFilter`'dan sonra çalışır, kimlik doğrulanmış kullanıcı için:
- `PLATFORM_ADMIN` → tüm tenant'lar (bypass)
- `ORG_OWNER` → `TenantContext.orgId == user.organizationId` olmalı
- Diğer roller → `TenantContext.salonId == user.salonId` olmalı
- Aksi halde `403`

**Test:** `TenantAccessFilterIT` — başka salona slug ile erişim `403`, kendi salonu `200`.

### P0.2 — JWT'nin tenant'a bağlanması

**Açık:** Token `salonId` claim'i taşıyordu ama doğrulanmıyordu; yalnızca username +
imza + expiry kontrol ediliyordu. Bir tenant için üretilen token başka tenant
bağlamında kabul edilebiliyordu.

**Çözüm:** [JwtService.validateToken](../../src/main/java/com/gscrm/security/JwtService.java)
artık token'daki `salonId` claim'ini yüklenen kullanıcının `salonId`'si ile
karşılaştırır (salon-bağımsız roller için claim yoksa atlanır).

**Test:** `JwtServiceTest.tokenRejectedWhenSalonMismatch`, `tokenValidWhenSalonMatches`.

### P0.3 — Zayıf secret'lara karşı fail-fast + encryption key ayrımı

**Açıklar:**
1. Prod'da repo'daki dev JWT secret'ı kullanılırsa engel yoktu.
2. `SecretEncryptionService`, depodaki şifreli sırları JWT secret'ından türetilen
   anahtarla şifreliyordu → JWT rotasyonu tüm şifreli sırları bozardı.

**Çözümler:**
- [ProductionSecretGuard.java](../../src/main/java/com/gscrm/config/ProductionSecretGuard.java)
  — `prod` profilinde dev secret / boş / çok kısa secret tespit ederse başlangıçta durur.
- [SecretEncryptionService.java](../../src/main/java/com/gscrm/security/SecretEncryptionService.java)
  — önce `app.encryption.key` (`APP_ENCRYPTION_KEY`) kullanır; yoksa geriye uyumluluk
  için JWT secret'a düşer ve uyarı loglar.

### P0.5 — Repo hijyeni

`cookies.txt`, `cookies2.txt`, `headers.txt`, `out.html` silindi; `system.txt`
git takibinden çıkarıldı. `.gitignore`'a debug artık kalıpları eklendi
(`cookies*.txt`, `headers*.txt`, `out.html`, `*.dump`).

---

## P1 — Sağlamlaştırma

### P1.1 — Test kapsamı
- `TenantAccessFilterIT` (cross-tenant list-endpoint)
- `JwtServiceTest` (salonId claim doğrulaması)

### P1.2 / P1.3 — Kota sayımında `findAll()` kaldırma
- `SubscriptionService.getUsage`: tüm `usage_meter` tablosunu belleğe çekip
  filtrelemek yerine `findByOrganizationIdAndPeriod` + DB tarafı `sumCount`.
- `QuotaEnforcementService.assertCanAddUser`: `userRepository.findAll()` yerine
  `countSeatUsersByOrganization` (CUSTOMER/PLATFORM_ADMIN hariç, DB `count`).

---

## Deploy notu — YENİ ZORUNLU ENV DEĞİŞKENLERİ

Prod `.env` dosyasına eklenmesi gereken **yeni değişken**:

```bash
# JWT'den bağımsız, güçlü rastgele anahtar (openssl rand -base64 48)
APP_ENCRYPTION_KEY=<...>
```

`docker-compose.yml` artık `APP_ENCRYPTION_KEY`'i zorunlu kılar (`:?`). Ayrıca
prod'da `JWT_SECRET` repo'daki dev değeri OLAMAZ (fail-fast).

---

## Kalan işler (bu güncelleme kapsamı DIŞINDA)

Bkz. [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md):
- Veri katmanı izolasyonu (Hibernate `@Filter` / PostgreSQL RLS) — servis-seviyesi
  manuel `salonId` filtresi hâlâ birincil savunma.
- Redis (rate limit + session + WebSocket) çok-instance ölçek için.
- MFA (ORG_OWNER / PLATFORM_ADMIN).
