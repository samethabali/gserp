# GSCRM — Proje Referans Dokümanı

> **Amaç:** Bu dosyayı okuyan bir geliştirici veya AI agent, GSCRM kod tabanı, mimarisi, veri modeli, API’ler, deploy ve iş kuralları hakkında **tek kaynaktan** bilgi sahibi olabilir.  
> **Son güncelleme:** 2026-06 · **Sürüm:** 1.0.0 (multi-tenant SaaS)  
> **Repo:** `gscrm` · **Paket:** `com.gscrm`

---

## İçindekiler

1. [Proje Özeti](#1-proje-özeti)
2. [Teknoloji Yığını](#2-teknoloji-yığını)
3. [Dizin Yapısı](#3-dizin-yapısı)
4. [Mimari Genel Bakış](#4-mimari-genel-bakış)
5. [Multi-Tenant Modeli](#5-multi-tenant-modeli)
6. [Veritabanı ve Flyway](#6-veritabanı-ve-flyway)
7. [Domain Modeli (Entity’ler)](#7-domain-modeli-entityler)
8. [Güvenlik ve Yetkilendirme](#8-güvenlik-ve-yetkilendirme)
9. [REST API Referansı](#9-rest-api-referansı)
10. [Web Arayüzü (Thymeleaf)](#10-web-arayüzü-thymeleaf)
11. [WebSocket / Canlı Takvim](#11-websocket--canlı-takvim)
12. [İş Modülleri (Servis Katmanı)](#12-iş-modülleri-servis-katmanı)
13. [Ticari Katman (Abonelik / Billing)](#13-ticari-katman-abonelik--billing)
14. [KVKK ve Uyumluluk](#14-kvkk-ve-uyumluluk)
15. [Platform / Franchise / Onboarding](#15-platform--franchise--onboarding)
16. [Konfigürasyon ve Ortam Değişkenleri](#16-konfigürasyon-ve-ortam-değişkenleri)
17. [Deploy ve Operasyon](#17-deploy-ve-operasyon)
18. [Test Stratejisi](#18-test-stratejisi)
19. [Dokümantasyon Haritası](#19-dokümantasyon-haritası)
20. [Geliştirme Kuralları ve Sık Yapılan Hatalar](#20-geliştirme-kuralları-ve-sık-yapılan-hatalar)

---

## 1. Proje Özeti

**GSCRM** (Güzellik Salonu CRM), güzellik/kuaför salonları için geliştirilmiş **multi-tenant SaaS** uygulamasıdır.

| Boyut | Açıklama |
|-------|----------|
| **Ne yapar?** | Randevu, personel, müşteri, ödeme, gider, ürün stoku, kampanya/sadakat, public online booking, müşteri portalı |
| **Kim kullanır?** | Bağımsız salonlar (STANDALONE) ve franchise zincirleri (FRANCHISE) |
| **Deploy modeli** | Tek Spring Boot instance + shared PostgreSQL; tek domain, kiracı oturum/JWT ile, public randevu `/b/{slug}` yoluyla ayrılır |
| **Monolit mi?** | Evet — tek JAR, Thymeleaf UI + REST API + WebSocket |

### Ürün evrimi

1. **v0** — In-memory demo (`MockDataStore`)
2. **v1 production-ready** — PostgreSQL + JPA + Flyway + Docker
3. **v1.0 multi-tenant** — `organization` / `salon` / `salon_id`, SaaS billing, KVKK consent registry, platform admin

---

## 2. Teknoloji Yığını

| Katman | Teknoloji | Sürüm / Not |
|--------|-----------|-------------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.4.5 |
| Web | Spring MVC, Thymeleaf | Sunucu-render UI |
| API | REST + Validation | `@RestController`, Jakarta Validation |
| Realtime | WebSocket + STOMP | SockJS, `/ws-calendar` |
| Persistence | Spring Data JPA, Hibernate | `ddl-auto: validate` (prod) |
| Migration | Flyway | `classpath:db/migration` (V1–V33) |
| DB | PostgreSQL | 16 |
| Security | Spring Security | Dual chain: JWT (API) + form login (web) |
| Token | jjwt | HS256, access + refresh |
| Metrics | Micrometer + Prometheus | `/actuator/prometheus` |
| Build | Maven | `mvn verify` |
| Container | Docker multi-stage | Alpine JRE, non-root user |
| CI | GitHub Actions | `ci.yml`, `staging.yml`, `deploy.yml` |
| Test | JUnit 5, Mockito, Testcontainers | PostgreSQL 16 alpine |

---

## 3. Dizin Yapısı

```
gscrm/
├── .github/workflows/          # CI, staging, deploy (SSH + docker compose)
├── docs/
│   ├── PROJECT_REFERENCE.md    # ← BU DOSYA
│   ├── deploy-vps.md           # VPS kurulum rehberi
│   └── saas/                   # SLA, DPA, pricing, DR, tek domain nginx
├── scripts/
│   ├── backup-db.sh            # pg_dump yedek
│   └── vps-setup.sh            # VPS otomasyon
├── src/main/java/com/gscrm/
│   ├── GscrmApplication.java
│   ├── config/                 # Seeder, Web, WebSocket
│   ├── controller/             # REST + PageController
│   ├── dto/                    # request/ response
│   ├── exception/              # GlobalExceptionHandler
│   ├── model/                  # JPA entity + enums
│   ├── repository/             # Spring Data JPA
│   ├── security/               # JWT, filters, RBAC
│   ├── service/                # Business logic + RetentionJob
│   └── tenant/                 # TenantContext, TenantFilter, TenantGuard
├── src/main/resources/
│   ├── application.yml         # Ortak config
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── logback-spring.xml
│   ├── db/migration/           # V1–V33 SQL
│   ├── db/dev-migration/       # V7 mock (sadece dev)
│   ├── static/js/              # calendar, booking, websocket
│   └── templates/              # Thymeleaf HTML
├── src/test/                   # Unit + integration tests
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── .env.example
├── README.md
└── system.txt                  # Kısa modül özeti (eski referans)
```

---

## 4. Mimari Genel Bakış

```
                    ┌─────────────────────────────────────────┐
                    │              nginx (SSL)                 │
                    │     gscrm.avesitesi.xyz → :8989          │
                    └───────────────────┬─────────────────────┘
                                        │
                    ┌───────────────────▼─────────────────────┐
                    │         gscrm-app (Spring Boot)          │
                    │  ┌─────────────┐  ┌──────────────────┐  │
                    │  │ TenantFilter│→ │ Security (JWT/   │  │
                    │  │ TenantMdc   │  │  Form + RBAC)    │  │
                    │  └─────────────┘  └──────────────────┘  │
                    │  Controllers → Services → Repositories   │
                    └───────────────────┬─────────────────────┘
                                        │
                    ┌───────────────────▼─────────────────────┐
                    │     PostgreSQL (shared, salon_id ile)    │
                    │  organization → salon → operasyonel veri   │
                    └─────────────────────────────────────────┘
```

### İstek yaşam döngüsü

1. **TenantFilter** — JWT `salonId` → oturum → adresteki slug (`/b/{slug}`, `salonSlug`, `X-Salon-Slug`) → `salon` kaydı → `TenantContext` (salonId, orgId, slug)
2. **TenantMdcFilter** — Log MDC’ye `salonId`, `orgId` yazar
3. **BookingRateLimitFilter** — `/api/booking/**` için salon+IP rate limit
4. **JwtAuthenticationFilter** — `/api/**` Bearer token (access token, `typ=access`)
5. **Controller** — `@PreAuthorize` + servis katmanında `TenantContext.requireSalonId()`
6. **TenantGuard / BranchScopeService** — cross-tenant IDOR engeli

---

## 5. Multi-Tenant Modeli

### Hiyerarşi

```
Platform (PLATFORM_ADMIN)
  └── Organization (STANDALONE | FRANCHISE)
        └── Salon (branch) ← asıl izolasyon birimi (salon_id)
              ├── Staff, Customer, Appointment, ...
              └── salon_settings (white-label)
```

### Tenant çözümleme (`TenantFilter`)

Tek ve kesin sıra. Alt alan adı, `*.localhost` ve sessiz `default` yedeği V33 ile
kaldırıldı: hiçbiri diğeriyle karşılaştırılmıyordu ve alt alan adı üretimde zaten
çalışmıyordu (wildcard DNS de sertifika da yok).

| Öncelik | Kaynak | Örnek |
|---------|--------|-------|
| 1 | Bearer JWT `salonId` claim'i | API istemcileri |
| 2 | Oturum `TENANT_SALON_ID` | Girişte yazılır; web isteklerinde tek yetkili kaynak |
| 3 | Adreste açık slug | `/b/kadikoy`, `?salonSlug=kadikoy`, `X-Salon-Slug: kadikoy` |
| 4 | Anonim ziyaretçinin seçtiği salon | Daha önce `/b/{slug}` ile girdiği salon |

Hiçbiri yoksa: sayfalar `/login`'e yönlenir, API `400 İşletme belirtilmedi` döner.

**Güvenlik:** kimlikli bir istekte açıkça verilen slug, oturum/JWT'deki salonla
uyuşmuyorsa istek `403` ile reddedilir. Önceden herkes başlık göndererek başka
kiracının public yüzeyine geçebiliyordu.

### Bypass (tenant zorunlu değil)

- `/actuator/**`
- `/api/platform/**` — platform admin
- `/api/onboarding/register` — yeni salon kaydı
- `/platform/**` web sayfaları — `platformBypass=true`
- `/login`, `/logout`, `/error`, `/onboarding/wizard`
- Statik dosyalar: `/css/**`, `/js/**`, `/images/**`, `/webjars/**`, `/favicon.ico`
  — bunlar eskiden bypass listesinde değildi; bilinmeyen bir slug'da CSS/JS bile
  JSON 404 dönüyor, giriş sayfası çizilemiyordu.

### Veri izolasyonu

- Tüm operasyonel tablolarda **`salon_id NOT NULL`** (V15)
- Unique constraint’ler **salon bazlı**: `(salon_id, username)`, `(salon_id, phone)`, `(salon_id, coupon.code)` (V16)
- Repository’ler: `findByIdAndSalonId`, `findBySalonIdAnd...`
- WebSocket topic’leri: `/topic/salon.{salonId}.appointments` (global topic yok)

### Rezerve slug’lar

`admin`, `api`, `www`, `platform`, `app`, `static`, `health`, `actuator`, `default` (pilot spec)

---

## 6. Veritabanı ve Flyway

**Konum:** `src/main/resources/db/migration/`  
**Prod:** V1–V33 · **Dev ek:** `db/dev-migration/V7__mock_data.sql`

| Versiyon | Dosya | Amaç |
|----------|-------|------|
| V1 | `V1__schema.sql` | staff, resource, service, customer, appointment, waitlist, working_hours, audit |
| V2 | `V2__users.sql` | users tablosu, staff FK |
| V3 | `V3__payments_and_customer_balance.sql` | payment, customer.balance |
| V4 | `V4__staff_specializations.sql` | staff ↔ service uzmanlık |
| V5 | `V5__expenses_and_products.sql` | expense, product, product_sale |
| V6 | `V6__fix_constraints_and_indexes.sql` | FK, unique, index, CHECK enum |
| V8 | `V8__product_improvements.sql` | cost_price, customer_id on sales |
| V9 | `V9__customer_portal.sql` | users.customer_id, customer.email unique |
| V10 | `V10__campaigns_and_loyalty.sql` | coupon, loyalty_tier, coupon_usage |
| V11 | `V11__user_password_flags.sql` | must_change_password |
| V12 | `V12__salon_settings_and_consent.sql` | salon_settings, customer.consent_at |
| V13 | `V13__notification_log.sql` | Bildirim logu (V28'de kaldırıldı) |
| V14 | `V14__organization_and_salon.sql` | organization + salon master, default seed |
| V15 | `V15__salon_id_columns.sql` | tüm tablolara salon_id + backfill |
| V16 | `V16__salon_composite_unique_indexes.sql` | per-salon unique/index |
| V17 | `V17__user_salon_role.sql` | user_salon_role, organization_owner |
| V18 | `V18__branch_overrides.sql` | branch_service_price, branch_holiday |
| V19 | `V19__coupon_scope.sql` | coupon scope SALON/ORG/GLOBAL |
| V20 | `V20__branch_stock.sql` | branch_stock, home_salon_id |
| V21 | `V21__loyalty_policy.sql` | organization.loyalty_policy SALON/ORG |
| V22 | `V22__onboarding_impersonation.sql` | onboarding_state, impersonation_log |
| V23 | `V23__subscription_billing.sql` | plan, subscription, usage_meter, billing_event |
| V24 | `V24__consent_registry.sql` | consent_record, salon contact/dpo |
| V29 | `V29__appointment_overlap_constraint.sql` | randevu çakışma kısıtı |
| V30 | `V30__phone_normalization.sql` | telefon normalizasyonu |
| V31 | `V31__phone_verification.sql` | doğrulama kodu, sms_log |
| V32 | `V32__appointment_body_regions.sql` | randevu bölge seçimi |
| V33 | `V33__invite_tracking_trial_and_audit.sql` | invite_redemption, invite_code.trial_days, activity_event scope/outcome/http_status, personel kullanıcı adı global unique |

### Varsayılan seed (V14)

- `organization.id=1` — "GSCRM Default", STANDALONE
- `salon.id=1`, slug=`default` — "GSCRM Salon"
- V23: org 1 → SOLO plan, ACTIVE

---

## 7. Domain Modeli (Entity’ler)

Tüm operasyonel entity’ler `TenantEntity` implement eder (`getSalonId()`).

| Entity | Tablo | Özet |
|--------|-------|------|
| `Organization` | organization | Franchise/bağımsız org, loyalty_policy |
| `Salon` | salon | Tenant birimi: slug, timezone, contact_email, dpo_name |
| `User` | users | Auth: username, role, salon_id, organization_id, staff_id, customer_id |
| `Staff` | staff | Personel, role (StaffRole), color_hex |
| `Customer` | customer | Müşteri, balance, consent_at, home_salon_id |
| `ServiceDefinition` | service_definition | Hizmet: süre, fiyat, kategori |
| `Resource` | resource | Oda/cihaz, kapasite |
| `Appointment` | appointment | Randevu, status, fiyat, session_group |
| `Payment` | payment | Tahsilat (CASH/CARD) |
| `Expense` | expense | Gider kalemleri |
| `Product` / `ProductSale` | product, product_sale | Stok ve satış |
| `Coupon` / `LoyaltyTier` | coupon, loyalty_tier | Kampanya; coupon.scope |
| `SalonSetting` | salon_settings | Key-value white-label |
| `BranchServicePrice` | branch_service_price | Şube fiyat override |
| `BranchStock` | branch_stock | Şube bazlı stok |
| `ConsentRecord` | consent_record | KVKK rıza kaydı |
| `AuditLogEntry` | audit_log_entry | Randevu audit (genişletilebilir) |
| `SubscriptionPlan` | subscription_plan | SOLO, FRANCHISE_STARTER, … |
| `OrganizationSubscription` | organization_subscription | Trial/active, trial_end |
| `UsageMeter` | usage_meter | Aylık kullanım sayacı |
| `OnboardingState` | onboarding_state | Kurulum sihirbazı adımı |

### Önemli enum’lar

**UserRole:** `PLATFORM_ADMIN`, `ORG_OWNER`, `BRANCH_MANAGER`, `ADMIN` (alias), `RECEPTIONIST`, `SPECIALIST`, `CUSTOMER`

**AppointmentStatus:** `PENDING_APPROVAL`, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`

**CouponScope:** `SALON`, `ORG`, `GLOBAL`  
**OrganizationType:** `STANDALONE`, `FRANCHISE`

---

## 8. Güvenlik ve Yetkilendirme

### İki filter chain (`SecurityConfig`)

| Chain | Matcher | Auth |
|-------|---------|------|
| Order 1 | `/api/**` | JWT (+ session IF_REQUIRED), CSRF cookie (auth/booking/webhook muaf) |
| Order 2 | Web sayfalar | Form login → `/login`, session |

### Rol matrisi (özet)

| Rol | Kapsam |
|-----|--------|
| PLATFORM_ADMIN | `/api/platform/**`, tenant provisioning |
| ORG_OWNER | `/api/org/**`, tüm org salonları özeti |
| BRANCH_MANAGER / ADMIN | Salon operasyonu (eski ADMIN) |
| RECEPTIONIST | Müşteri, ödeme, booking onay |
| SPECIALIST | Kendi randevuları (`StaffScopeService`) |
| CUSTOMER | `/api/customer/**`, portal |

### JWT (`JwtService`)

| Claim | Değer |
|-------|-------|
| `typ` | `access` veya `refresh` |
| `sub` | username |
| `salonId`, `orgId`, `role`, `staffId`, `customerId` | Tenant + identity |

- Access: 60 dk · Refresh: 7 gün (`app.jwt.*`)
- Refresh yalnızca `/api/auth/refresh`; API endpoint’lerinde access token zorunlu

### Scope servisleri

- **`StaffScopeService`** — SPECIALIST yalnızca kendi staff_id randevuları
- **`BranchScopeService`** — ORG_OWNER org salonları; PLATFORM_ADMIN bypass
- **`TenantGuard`** — `assertSameSalon(entity.getSalonId())`

### Public endpoint’ler

`/api/auth/**`, `/api/booking/**`, `/api/settings/public`, `/api/webhooks/**`, `/api/onboarding/register`

---

## 9. REST API Referansı

> Tenant-scoped istekler kiracıyı oturumdan (web) ya da Bearer JWT'den (API) alır.
> Public randevu uçlarında `salonSlug` parametresi veya `X-Salon-Slug` başlığı
> zorunludur. Kimlikli bir istekte gönderilen slug kimliğin salonuyla
> uyuşmuyorsa istek 403 döner.

### Auth — `/api/auth`
| Method | Path | Açıklama |
|--------|------|----------|
| POST | `/login` | Staff JWT |
| POST | `/refresh` | Access token yenile |
| POST | `/change-password` | Parola değiştir (min 8 karakter) |

### Customer auth — `/api/auth/customer`
| POST | `/register`, `/login` | Müşteri portal JWT |

### Public booking — `/api/booking`
| GET | `/services`, `/staff`, `/availability` | Müsaitlik |
| POST | `/request` | Randevu isteği → PENDING_APPROVAL + consent |

### Randevu — `/api/appointments`
| GET | `/`, `/history` | Liste |
| POST | `/` | Oluştur |
| PUT | `/{id}/move`, `/{id}` | Taşı / güncelle |
| PATCH | `/{id}/status`, `/{id}/approve`, `/{id}/reject` | Durum |
| DELETE | `/{id}` | İptal |

### Müşteri — `/api/customers`
| CRUD + `/lookup` | |
| GET | `/{id}/export` | KVKK veri taşınabilirliği |
| DELETE | `/{id}/gdpr` | Anonimleştirme |
| POST | `/{id}/consent/revoke` | Rıza geri çek |

### Dashboard — `/api/dashboard`
| GET | `/today`, `/`, `/sessions`, `/trend` | KPI |

### Kampanya — `/api/campaigns`
| CRUD coupons, loyalty-tiers | |
| POST | `/validate` | Kupon doğrula |
| GET | `/loyalty-info` | Sadakat bilgisi |

### Ödeme / Gider / Ürün / Personel / Hizmet / Kaynak / Waitlist
Standart CRUD pattern — bkz. controller sınıfları: `PaymentController`, `ExpenseController`, `ProductController`, `StaffController`, `ServiceController`, `ResourceController`, `WaitlistController`

### Platform — `/api/platform` (PLATFORM_ADMIN)
| GET | `/tenants` | Tenant listesi |
| POST | `/tenants` | Yeni org+salon provision |

### Org — `/api/org` (ORG_OWNER)
| GET | `/salons`, `/summary` | Franchise HQ |

### Onboarding — `/api/onboarding`
| POST | `/register` | Self-service kayıt (tenant filter bypass) |
| GET/PUT | `/steps` | Sihirbaz adımları |

### Billing — `/api/billing`
| GET | `/plan`, `/usage` | Abonelik ve kullanım |

### Stok — `/api/inventory`
| POST | `/transfer` | Şubeler arası stok transfer |

### Actuator
`/actuator/health`, `/actuator/info`, `/actuator/prometheus`

**Standart yanıt:** `ApiResponse<T>` — `{ success, message, data, error }`

---

## 10. Web Arayüzü (Thymeleaf)

| Route | Template | Rol (web chain) |
|-------|----------|-----------------|
| `/` | index.html | Takvim (FullCalendar) |
| `/login` | login.html | Public |
| `/dashboard` | dashboard.html | Staff |
| `/customers`, `/staff`, `/services`, `/resources` | | RECEPTIONIST+ |
| `/settings`, `/users`, `/audit` | | Management |
| `/booking` | booking.html | Public |
| `/privacy` | privacy.html | KVKK (Thymeleaf salon değişkenleri) |
| `/customer/*` | customer-*.html | CUSTOMER |
| `/platform/tenants` | platform/tenants.html | PLATFORM_ADMIN |
| `/org/dashboard` | org/dashboard.html | ORG_OWNER |
| `/onboarding/wizard` | onboarding/wizard.html | Public/authenticated |

**Fragment:** `fragments/sidebar-nav.html` — rol bazlı menü

**Static JS:** `static/js/` — `calendar.js`, `booking.js`, `websocket.js` (salon topic subscribe)

---

## 11. WebSocket / Canlı Takvim

| Bileşen | Değer |
|---------|-------|
| Endpoint | `/ws-calendar` (SockJS) |
| Broker prefix | `/topic` |
| App prefix | `/app` |
| Client → server | `/app/appointment/move` |
| Server → client | `/topic/salon.{salonId}.appointments` |
| Dashboard refresh | `/topic/salon.{salonId}.dashboard` |
| Bildirimler | `/topic/salon.{salonId}.notifications` |

nginx’de WebSocket proxy upgrade gerekir (`docs/deploy-vps.md`).

---

## 12. İş Modülleri (Servis Katmanı)

| Servis | Sorumluluk |
|--------|------------|
| `AppointmentService` | CRUD, taşıma, onay/red, çoklu seans, PENDING_APPROVAL booking |
| `SchedulerService` | Çalışma saati, müsaitlik, overlap |
| `ResourceLockService` | Kaynak kapasite kilidi |
| `CustomerService` | CRM, arama, detay |
| `PaymentService` | Tahsilat, günlük özet |
| `ExpenseService` | Gider + kategori özeti |
| `ProductService` | Stok, satış, düşük stok |
| `InventoryService` | branch_stock transfer |
| `CampaignService` | Kupon, sadakat, scope |
| `BranchPricingService` | Şube fiyat override |
| `StaffService` | Personel, uzmanlık, working_hours |
| `SalonSettingsService` | White-label key-value (salon scoped) |
| `DashboardService` | KPI + `getOrgSummary` |
| `UserService` | Staff kullanıcı + kota kontrolü |
| `SalonProvisioningService` | Yeni tenant: org, salon, admin, plan, onboarding |
| `ConsentService` | Booking/portal rıza kaydı |
| `GdprService` | Export + anonimleştirme |
| `SubscriptionService` | Plan, usage, abonelik aktifleştirme |
| `QuotaEnforcementService` | Seat, salon kotası |
| `AuditService` | Randevu değişiklik logu |
| `AppointmentReminderService` | 24h cron hatırlatma |
| `RetentionJob` | Eski audit log silme (90 gün) |
| `NotificationService` | WebSocket broadcast |

## 13. Ticari Katman (Abonelik / Billing)

### Planlar (V23 seed)

| Kod | Salon | User |
|-----|-------|------|
| SOLO | 1 | 5 |
| FRANCHISE_STARTER | 5 | 20 |
| FRANCHISE_PRO | 999 | 999 |

### Akış

1. Provision / onboarding → `organization_subscription` TRIAL (14 gün)
2. `UserService.create` → `QuotaEnforcementService.assertCanAddUser`
3. `POST /api/billing/activate` (PLATFORM_ADMIN) → abonelik `ACTIVE` + `billing_event` kaydı

**Fiyatlandırma dokümanı:** `docs/saas/pricing.md`

---

## 14. KVKK ve Uyumluluk

| Özellik | Uygulama |
|---------|----------|
| Aydınlatma | `/privacy` — salon adı, DPO, alt işleyenler (Thymeleaf) |
| Rıza | `consent_record` — PRIVACY, MARKETING, REMINDER |
| Booking rıza | `AppointmentCreateRequest.consentTypes` → `ConsentService` |
| Export | `GET /api/customers/{id}/export` |
| Silme | `DELETE /api/customers/{id}/gdpr` — anonimleştirme |
| Rıza iptal | `POST /api/customers/{id}/consent/revoke` |
| Retention | `RetentionJob` — 90 gün audit/notification purge |
| Hukuk taslakları | `docs/saas/dpa.md`, `franchise-agreement.md` |

---

## 15. Platform / Franchise / Onboarding

### Bağımsız salon (STANDALONE)

1. `POST /api/onboarding/register` veya platform admin `POST /api/platform/tenants`
2. `SalonProvisioningService` → org + salon + BRANCH_MANAGER + settings + trial
3. `/onboarding/setup` — salon, hizmet, personel

### Franchise (FRANCHISE)

- `ORG_OWNER` → `/org/dashboard`, `/api/org/summary`
- Merkezi kupon (`coupon.scope=ORG`)
- `loyalty_policy=ORG` → sadakat org geneli
- `InventoryService.transfer` → şubeler arası stok

### Pilot senaryolar

`docs/saas/pilot-scenarios.md`

---

## 16. Konfigürasyon ve Ortam Değişkenleri

### Profiller

| Profil | Kullanım |
|--------|----------|
| `dev` | Yerel PG, DevDataSeeder, Thymeleaf cache off |
| `prod` | Env datasource, log file, Thymeleaf cache on |
| `test` | Testcontainers PostgreSQL |

### Kritik env (`.env.example`)

```
DB_PASSWORD, SPRING_DATASOURCE_*
JWT_SECRET
APP_CORS_ALLOWED_ORIGINS
GSCRM_INITIAL_ADMIN_USERNAME/PASSWORD
SPRING_PROFILES_ACTIVE=prod
```

### application.yml özeti

- Port: 8989
- JWT: `app.jwt.secret`, access 60m, refresh 7d
- Actuator: health, info, **prometheus**
- Flyway: enabled, baseline-on-migrate

---

## 17. Deploy ve Operasyon

### Yerel geliştirme

```bash
docker compose -f docker-compose.dev.yml up -d db
mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# http://localhost:8989 · admin/admin123 (dev seed)
# Header: X-Salon-Slug: default
```

### Production Docker

```bash
cp .env.example .env
docker compose up -d --build
curl http://127.0.0.1:8989/actuator/health
```

### VDS (Emre deseni)

- **Multi-tenant:** tek instance, tek domain nginx (`docs/saas/nginx-single-domain.md`)
- **MCP:** `deploy_kurallari.yaml` → `gscrm`, image_transfer, port 5004
- **CD:** `.github/workflows/deploy.yml` — SSH git pull + compose
- **Yedek:** `scripts/backup-db.sh` — 14 gün retention
- **DR:** `docs/saas/dr-playbook.md`

### Monitoring

- `/actuator/health` — liveness/readiness
- `/actuator/prometheus` — Metrikler (prod nginx’de kısıtla)
- Log: `LOG_DIR` / MDC `salonId`, `orgId`

---

## 18. Test Stratejisi

| Test | Kapsam |
|------|--------|
| `BookingControllerTest` | Booking request JSON |
| `AppointmentServiceTest` | PENDING_APPROVAL + salonId |
| `TenantFilterTest` | Slug 404/200 |
| `TenantIsolationIT` | Cross-tenant staff reddi |
| `SecurityConfigIT` | Platform auth, public booking |
| `JwtServiceTest` | Access/refresh ayrımı |

**Çalıştırma:** `mvn verify` (Surefire: `*Test.java`, `*IT.java`)

**DB:** Testcontainers `jdbc:tc:postgresql:16-alpine:///gscrm`

---

## 19. Dokümantasyon Haritası

| Dosya | Ne zaman oku |
|-------|--------------|
| **docs/PROJECT_REFERENCE.md** | Her şey — bu dosya |
| README.md | Hızlı başlangıç |
| system.txt | Kısa modül listesi |
| docs/deploy-vps.md | VPS/nginx/SSL |
| docs/saas/* | SaaS iş/hukuk/ops |
| CHANGELOG.md | Sürüm geçmişi |

---

## 20. Geliştirme Kuralları ve Sık Yapılan Hatalar

### Yapılması gerekenler

1. **Yeni entity** → `salon_id` + `TenantEntity` + migration
2. **Yeni repository sorgusu** → `salonId` parametresi zorunlu
3. **Yeni public API** → TenantFilter bypass gerekir mi? dikkat
4. **WebSocket topic** → `/topic/salon.{id}.*` pattern
5. **Migration** → asla uygulanmış SQL’i değiştirme; V25+ ekle
6. **Test** → `X-Salon-Slug: default` header integration testlerde

### Yapılmaması gerekenler

1. `findById(id)` tenant kontrolsüz kullanma → IDOR
2. Global `findAll()` operasyonel veride
3. Refresh token’ı API endpoint’lerinde kabul etme
4. Secrets’ı git’e commit etme (`.env`)
5. `target/` commit etme

### Kod konvansiyonları

- Lombok: `@Builder`, `@RequiredArgsConstructor` entity/service’lerde yaygın
- API yanıt: `ApiResponse.ok()` / `ApiResponse.error()`
- Exception: `ConflictException`, `ResourceNotAvailableException` → `GlobalExceptionHandler`
- Audit: randevu değişikliklerinde `AuditService.log()`

---

## Hızlı Referans Kartı

```
Tenant (public):   /b/default  ya da  X-Salon-Slug: default
Staff login API:   POST /api/auth/login
Public booking:    POST /api/booking/request
Provision tenant:  POST /api/platform/tenants (PLATFORM_ADMIN)
Default salon:     id=1, slug=default
Flyway latest:     V33
Java package:      com.gscrm
Port:              8989
```

---

*Bu doküman GSCRM kod tabanıyla senkron tutulmalıdır. Büyük mimari değişikliklerde güncelleyin.*
