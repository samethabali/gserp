# GSCRM — Ürünleştirme & SaaS Yol Haritası

**Son güncelleme:** 2026-06-24  
**Branch:** `production-ready`  
**Canlı:** `https://gscrm.avesitesi.xyz` · tenant: `https://default.gscrm.avesitesi.xyz`

Bu doküman demo hazırlığı, deploy ve SaaS ürünleştirme adımlarını tek planda toplar.

---

## 1. Demo hazırlığı

### Tamamlanan

| Madde | Durum |
|-------|--------|
| UX paketi (custom select, takvim, müşteri seçici) | ✅ |
| Demo rehberi (`docs/DEMO.md`) + `admin`/`admin` | ✅ |
| Demo testleri (`DemoFeaturesIT`, unit testler) | ✅ |
| Billing UI (`/settings/billing`) | ✅ |
| Trial salt okunur mod | ✅ |
| 5 adımlı onboarding (`/onboarding/setup`) | ✅ |

### Demo öncesi kontrol listesi

- [x] `mvn verify` yeşil
- [ ] `.\start-dev.ps1` → lokal smoke (15 dk akış, `docs/DEMO.md`)
- [ ] Commit + push `production-ready`
- [ ] VDS deploy healthy (`/actuator/health`)
- [ ] Prod'da giriş / booking / takvim smoke
- [ ] Arkadaşlar için LAN veya VDS URL paylaş

### Demo URL'leri

| Ortam | Giriş | Tenant |
|-------|--------|--------|
| Lokal | http://localhost:8989/login | `default` (otomatik) |
| VDS (ana) | https://gscrm.avesitesi.xyz/login | header/subdomain |
| VDS (demo salon) | https://default.gscrm.avesitesi.xyz/login | `default` |

---

## 2. Deploy

### Otomatik (önerilen)

`production-ready` push → CI (`mvn verify`) → **Deploy to VPS** workflow.

Gerekli GitHub secrets: `VDS_HOST`, `VDS_USER`, `VDS_SSH_KEY`, opsiyonel `VDS_SSH_PORT`.

Manuel tetik: Actions → **Deploy to VPS** → Run workflow.

### Alternatif: MCP image_transfer

```powershell
# MCP vds-deploy sunucusu üzerinden
deploy_yap(proje="gscrm", deploy_yontemi="image_transfer")
```

Profil: `deploy_kurallari.yaml` → port **5004**, domain `gscrm.avesitesi.xyz`, wildcard `*.gscrm.avesitesi.xyz`.

### Prod demo verisi

Prod DB'de demo kullanıcı yoksa:

1. `/onboarding/wizard` ile yeni tenant kaydı, veya
2. VDS'te dev seed **kullanma** — yalnızca onboarding / manuel admin

---

## 3. Ürünleştirme dalgaları

### Dalga A — Gelir & onboarding (MVP ticari)

**Hedef:** İlk ücretli pilot müşteri alınabilir durum.

| # | İş | Öncelik | Efor | Bağımlılık |
|---|-----|---------|------|------------|
| A1 | Elle abonelik aktifleştirme (`POST /api/billing/activate`) → `ACTIVE` | P0 | S | Billing UI ✅ | ✅ |
| A2 | Trial bitiş e-postası / banner iyileştirme | P1 | S | A1 | ✅ `TrialExpiryNotifier` |
| A3 | Pilot seed Senaryo A (`DevDataSeeder`) | P1 | M | Onboarding ✅ | ✅ `PilotScenarioSeeder` |
| A4 | Onboarding sonrası otomatik yönlendirme (login → setup) | P2 | S | A3 | ✅ |
| A5 | Hizmet şablonu (Saç + Cilt) provisioning'de | P2 | M | A3 | ✅ |

**Kabul kriteri:** Yeni salon kayıt → 14 gün trial → ödeme → yazma devam; kota aşımında yazma durur.

### Dalga B — Franchise & multi-şube

**Hedef:** 2+ şubeli işletme pilotu (Senaryo B).

| # | İş | Öncelik | Efor |
|---|-----|---------|------|
| B1 | Pilot seed Senaryo B (Belleza Chain) | P0 | M | ✅ |
| B2 | Salon switcher UI (ORG_OWNER / çok şubeli kullanıcı) | P0 | L | ✅ |
| B3 | Org dashboard KPI iyileştirme (şube karşılaştırma) | P1 | M | ✅ |
| B4 | Platform impersonation (support) | P1 | M | ✅ |
| B5 | Franchise kupon org scope doğrulama testleri | P1 | S | ✅ `CampaignOrgScopeIT` |
| B6 | Cross-tenant erişim audit + otomatik test | P1 | M | ✅ `FranchiseIsolationIT` |

**Kabul kriteri:** Kadıköy manager Beşiktaş randevusuna 403; org owner iki şubeyi görebilir.

### Dalga C — İş kuralları & operasyon

| # | İş | Öncelik | Efor |
|---|-----|---------|------|
| C1 | `BranchPricingService` → randevu fiyatlandırma | P1 | M | ✅ |
| C2 | `branch_holiday` model + UI + scheduler entegrasyonu | P1 | L | API ✅, UI kısmi |
| C3 | Kullanıcı / şube kotası UI uyarıları | P2 | S | ✅ billing quotas |
| C4 | Usage meter dashboard (aktif kullanıcı) | P2 | M |
| C5 | Faturalandırma olayları admin görünümü | P2 | M | ✅ billing events |

### Dalga D — Güvenlik & ölçek (prod sertleştirme)

| # | İş | Öncelik | Efor | Referans |
|---|-----|---------|------|----------|
| D1 | Sır şifreleme (at rest) | P0 | M | `salon_settings` | ✅ (+ `APP_ENCRYPTION_KEY` ile JWT'den bağımsız anahtar) |
| D2 | JWT secret rotation playbook | P1 | S | `docs/saas/dr-playbook.md` | ✅ `docs/saas/jwt-rotation.md` (+ prod fail-fast guard) |
| D3 | Redis rate limit + session (booking dışı) | P1 | L | — |
| D4 | MFA (ORG_OWNER, PLATFORM_ADMIN) | P2 | L | — |
| D5 | DR: PG backup otomasyonu + restore testi | P1 | M | `scripts/backup-db.sh` | ✅ `scripts/verify-backup.sh` |
| D6 | KVKK DPA imza akışı / veri export API | P2 | L | `docs/saas/dpa.md` |
| D7 | SLA monitoring + uptime raporu | P2 | M | `docs/saas/sla.md` |

### Dalga E — Platform & büyüme

| # | İş | Öncelik | Efor |
|---|-----|---------|------|
| E1 | Platform tenant yönetimi UI (suspend, plan değiştir) | P1 | L |
| E2 | Self-service plan upgrade (SOLO → FRANCHISE) | P2 | L |
| E3 | Staging ortamı + smoke (`staging.yml`) | P2 | M |
| E4 | Landing / pazarlama sayfası (`gscrm.avesitesi.xyz`) | P3 | M |
| E5 | ENTERPRISE dedicated instance şablonu | P3 | XL |

---

## 4. SaaS teknik borç & iskeletler

8 fazlı plan commit `0ad77f3` ile “tamam” işaretli; aşağıdakiler **kabul kriteri** seviyesinde henüz bitmedi:

| Alan | Mevcut | Eksik |
|------|--------|-------|
| Abonelik | API + UI + read-only + elle aktifleştirme | Fatura PDF |
| Kota | Kullanıcı/şube sayacı | Kota UI enforcement |
| Onboarding | 5 adım wizard | Login redirect, hizmet şablonu |
| Franchise | Org API, branch scope | Switcher, impersonation, seed B |
| Tenant | `X-Salon-Slug`, subdomain, ✅ cross-tenant erişim engeli (`TenantAccessFilter`), ✅ JWT-tenant bağı | Custom domain (CNAME), veri katmanı RLS |
| Bildirim | Uygulama içi (WebSocket) | SMS, e-posta transactional |
| Audit | `audit_log` API | Platform-wide arama, export |

---

## 5. Öncelik sırası (önerilen sprint)

```
Sprint 1 (demo + deploy)     → ✅ commit, push, VDS smoke
Sprint 2 (pilot A)           → ✅ A3 seed, A4 redirect, A5 şablon (+ Senaryo B seed)
Sprint 3 (ödeme)             → ✅ A1 elle aktifleştirme, A2 trial uyarıları
Sprint 4 (franchise pilot)   → ✅ B2 switcher, B3 org dashboard, B5–B6 testler
Sprint 5 (güvenlik)          → ✅ D1 sır şifreleme, D5 backup verify script
```

---

## 6. İlgili dokümanlar

| Doküman | İçerik |
|---------|--------|
| [DEMO.md](DEMO.md) | 15 dk demo akışı |
| [PROJECT_REFERENCE.md](PROJECT_REFERENCE.md) | Mimari referans |
| [saas/pilot-scenarios.md](saas/pilot-scenarios.md) | Senaryo A/B |
| [saas/pricing.md](saas/pricing.md) | Plan & metrikler |
| [deploy-vps.md](deploy-vps.md) | VPS kurulum |
| [saas/nginx-wildcard.md](saas/nginx-wildcard.md) | Wildcard subdomain |

---

## 7. Metrikler (pilot başarı)

| Metrik | Hedef |
|--------|--------|
| Onboarding tamamlama | > %80 kayıt → COMPLETED |
| Trial → paid dönüşüm | > %20 (ilk 3 pilot) |
| Cross-tenant ihlal | 0 (otomatik test yeşil) |
| Deploy süresi | < 5 dk, health UP |
