# GSCRM — Yapılacaklar

**Son güncelleme:** 2026-09-05
**Bağlam:** Pilot müşteriye davet kodu gönderilmeden önce kapatılması gereken maddeler.

Öncelik sırası: P0 müşteriye gitmeden, P1 pilot sürerken, P2 sonrası.

---

## P0 — Müşteriye göndermeden önce

### 1. Deploy sırasında site 502 veriyor → en az 2 konteynerli koşum

**Belirti:** Deploy sırasında `https://gscrm.avesitesi.xyz` ~90 saniye **502 Bad Gateway** dönüyor.
2026-09-05'te canlıda görüldü (deploy `f57c5e6`, 08:25–08:29 UTC).

**Sebep:** Tek `gscrm-app` konteyneri var. `docker compose up -d` konteyneri
yeniden yaratıyor, nginx `proxy_pass http://127.0.0.1:8989` boşluğa vuruyor ve
uygulama ayağa kalkana kadar (ölçülen: ~90 sn, soğuk açılış 128 sn) 502 dönüyor.

**Yapılacak:** Uygulamayı **en az 2 konteynerle** koşup deploy'u sırayla yap.

- `docker-compose.prod.yml`: `app` servisini iki örneğe çıkar (örn. `app-blue` /
  `app-green` ya da farklı host portlarına bağlı iki replika — 8989 ve 8990).
- nginx'te tek `proxy_pass` yerine `upstream` tanımla, iki örneği de yaz;
  `max_fails` / `fail_timeout` ile sağlıksız olanı devre dışı bıraksın.
  Dosya: `docs/saas/nginx-single-domain.md:50` ve `:58`.
- `.github/workflows/deploy.yml`: örnekleri **teker teker** güncelle — birini
  durdur, yenile, `/actuator/health` yeşile dönene kadar bekle, sonra diğerine geç.
  Şu an tek adımda hepsini yeniden yaratıyor.

**Kabul kriteri:** Deploy sürerken saniyede bir `curl /login` at; tek bir 502 bile
görülmemeli.

> Not: `main` varsayılan dal olduğu için `workflow_run` ile tetiklenen deploy
> workflow'u **main'deki sürümü** çalıştırır. Deploy script'inde yapılan değişiklik
> `main`'e girmeden etkili olmaz. (2026-09-05'te `f57c5e6` ile senkronlandı.)

---

### 2. Uçtan uca pilot akış testi yok

**Neden önemli:** 2026-09-04/05'te ana kullanıcı akışlarında 6 gerçek hata çıktı ve
**hiçbirini 245 test yakalamadı** (aşağıya bkz.). Hepsi katmanlar arası bağlantı
hatasıydı: şablona geçmeyen değişken, yanlış yönlendirme hedefi, sabit kodlanmış meta.
Birim testleri bu sınıfı göremiyor.

**Yapılacak:** Tek bir IT, müşterinin izlediği yolu baştan sona yürüsün:

1. Platform panelinden davet kodu üret
2. Kodla kayıt ol (`/api/onboarding/register`)
3. **Gerçek form girişi** yap (`POST /login`) ve yönlendirmeyi takip et
4. Parola değiştir, dönülen `nextUrl`'e git
5. Kurulum sihirbazını tamamla
6. Hizmet + personel ekle
7. Public `/{slug}` sayfasından randevu al
8. Randevu takvimde/panelde görünsün

**Kabul kriteri:** Bu test, aşağıdaki 6 hatanın en az 5'ini yakalayabilmeli.

---

### 3. SMS / OTP doğrulaması ya test edilmeli ya kapalı tutulmalı

`/api/booking/verify/start` ve `/api/booking/verify/confirm` uçlarına **hiçbir test
dosyası dokunmuyor.** Açıksa randevu akışının tam ortasında duruyor.

**Yapılacak:** Pilot müşteride `booking.sms_verification_enabled` ayarının kapalı
olduğunu doğrula; ya da uçları teste bağla.

---

## P1 — Bilinen açık hata

### 4. Platform yöneticisi canlı takvim güncellemesi alamıyor

`TenantTopicInterceptor` abonelik adresindeki salon kimliğini oturumun `salonId`'siyle
karşılaştırıyor. PLATFORM_ADMIN'in `salonId`'si `NULL` olabildiği için (V34) bir
kiracının takvimine baktığında `/topic/salon.{id}.*` aboneliği reddediliyor ve sağ
üstte kırmızı **"Bağlantı kesildi"** görünüyor.

Normal salon kullanıcısını etkilemiyor (yeşil "Bağlı" doğrulandı).

**Yapılacak:** Platform yöneticisine (ve şube değiştiren ORG_OWNER'a) o an
görüntülediği kiracının kanalına abone olma izni ver — istek bağlamındaki
`TenantContext.getSalonId()` üzerinden, istemcinin verdiği adrese güvenmeden.

Dosya: `src/main/java/com/gscrm/config/TenantTopicInterceptor.java`

---

## P2 — Test boşlukları

Hiçbir test dosyasının dokunmadığı uçlar:

| Uç | Not |
|---|---|
| `/api/booking/verify/*` | P0-3'te ayrıca ele alındı |
| `/api/waitlist/**` | Bekleme listesi |
| `/api/inventory/**` | Stok |
| `/api/audit/**` | İşlem kütüğü |
| `/api/org/**` | Organizasyon / şube |
| `/api/customer/**` | Müşteri portalı |
| Şube tatilleri | Kapalı gün kontrolü |

Ayrıca: **gerçek form girişi akışını** test eden hiçbir test yok.
`UiSecurityBehaviourIT` hız sınırını ve token davranışını kapsıyor, ama
"kullanıcı adı-parola gir → nereye düşüyorsun" akışını kapsamıyor. 4 numaralı
hata (aşağıda) tam bu boşluktan kaçtı.

---

## Bağlam: 2026-09-04/05'te bulunup düzeltilenler

Hepsi ana kullanıcı akışlarındaydı, hiçbiri testlerce yakalanmamıştı.

| # | Hata | Etki | Commit |
|---|------|------|--------|
| 1 | Parola değiştirmede hata mesajı `showToast` ile veriliyordu ama sayfada `toastContainer` yok | Kullanıcı 400'ün sebebini göremiyordu | `3969244` |
| 2 | `autocomplete` ipucu yoktu; parola yöneticisi "Mevcut Parola"yı eski parolayla dolduruyordu | Parola değiştirilemiyordu | `3aa0fb4` |
| 3 | Salonsuz platform yöneticisi girişten sonra `/` → TenantFilter → `/login` döngüsüne düşüyordu | Panele **hiç girilemiyordu** | `17d8f64` |
| 4 | `index.html`'de `salon-id` meta'sı sabit `1`; ayrıca SUBSCRIBE denetlenmiyordu | id≠1 kiracıda canlı güncelleme yok + salon 1'in müşteri adları başka kiracıların ekranında | `d62fdb5` |
| 5 | Menüdeki booking bağlantısı kiracısız `/booking`'e gidiyordu; randevu linki `localhost:8989` gösteriyordu | Salon sahibi adresini paylaşamıyordu | `28bb671` |
| 6 | `/booking` adres çubuğunda kalıyordu; ara sayfadan dönen ziyaretçi işletmesiz adrese düşüyordu | "İşletme seçilmedi" hatası | `3462bbe` |

**Ortak nokta:** Altısı da katmanlar arası bağlantı hatası. P0-2'deki uçtan uca test
bu sınıfı hedefliyor.
