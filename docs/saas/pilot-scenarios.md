# GSCRM SaaS — Pilot Senaryo Spec

## Senaryo A: Bağımsız Salon (STANDALONE)

| Alan | Değer |
|------|-------|
| Org adı | Güzellik Atölyesi |
| Org tipi | STANDALONE |
| Salon slug | `guzellik-atolyesi` |
| Subdomain | `guzellik-atolyesi.gscrm.avesitesi.xyz` |
| Plan | SOLO (trial 14 gün) |
| İlk admin | `admin@guzellik-atolyesi` / geçici parola |

**Test akışı:**
1. Self-service onboarding → org + salon + admin
2. Hizmet menüsü şablonu (Saç + Cilt)
3. Public booking → randevu → consent kaydı
4. WhatsApp hatırlatma (test numarası)
5. Günlük dashboard KPI

## Senaryo B: Franchise (2 şube)

| Alan | Şube 1 | Şube 2 |
|------|--------|--------|
| Org adı | Belleza Chain | Belleza Chain |
| Org tipi | FRANCHISE | FRANCHISE |
| Salon slug | `belleza-kadikoy` | `belleza-besiktas` |
| ORG_OWNER | `owner@belleza` | — |
| BRANCH_MANAGER | `mgr-kadikoy` | `mgr-besiktas` |

**Test akışı:**
1. Platform admin → org + 2 salon provision
2. ORG_OWNER login → org-summary dashboard (2 şube ciro karşılaştırma)
3. ORG scope kupon `BELLEZA10` her iki şubede geçerli
4. Cross-tenant: Kadıköy manager Beşiktaş randevusuna erişemez (403)
5. Müşteri aynı telefon ile iki şubede kayıt (franchise policy)

## Rezerve Slug Listesi

`admin`, `api`, `www`, `platform`, `app`, `static`, `health`, `actuator`, `default`

## Test Verisi Seed (dev profil)

`DevDataSeeder` genişletmesi: Senaryo A + B minimal dataset (Faz 4 sonrası onboarding test)
