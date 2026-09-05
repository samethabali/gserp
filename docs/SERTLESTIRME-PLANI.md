# Sertleştirme planı — güvenlik, yük ve kriz hazırlığı

**Tarih:** 2026-09-05 — *son güncelleme: aynı gün, gece*
**Kapsam:** Pilot müşteriler sisteme girmeden önce kapatılması gereken açıklar ve
kriz anında elimizde olması gereken şeyler.

Aşağıdakiler tahmin değil; her madde kodda veya üretimde doğrulandı. Doğrulandığı
yer madde içinde yazıyor.

## Durum özeti

| # | Madde | Durum |
|---|---|---|
| A1 | Çıktı kaçışı (XSS) | ✅ Bitti — canlıda |
| A2 | Yetki matrisi testi | ✅ Bitti — 121 test, canlıda |
| A3 | Hız sınırı kapsamı | ✅ Bitti + ziyaretçi bazlı kova düzeltmesi |
| A4 | Dependabot / CodeQL | ✅ Kullanıcı açtı |
| A5 | Origin'i Cloudflare'e kapatma | ⏳ Sunucu tarafı — aşağıda |
| B1 | Yük testi | ✅ Bitti — [yuk-testi-sonuclari.md](yuk-testi-sonuclari.md) |
| B2 | Bellek limiti doğrulaması | ⏳ SSH görevi #3, İş 4 |
| B3 | İndeks ve sorgu kontrolü | ✅ Bitti — B1 kapsamında |
| C1 | İzleme / uyarı | ⛔ Kullanıcı şimdilik istemedi |
| C2 | Geri yükleme provası | ⏳ SSH görevi #3, İş 1 |
| C3 | Olay kılavuzu | ✅ Bitti — [olay-kilavuzu.md](olay-kilavuzu.md) |

Ayrıca planda olmayan, ölçüm sırasında çıkan bir bulgu düzeltildi: statik
dosyalar hiç önbelleklenmiyordu (bkz. B1 sonuçları).

---

## Önce: iyi durumda olanlar

Boşuna iş yapmamak için, bakılıp **sorun bulunmayan** yerler:

| Alan | Durum |
|---|---|
| Kiracı izolasyonu | 5 ayrı IT sınıfı, en iyi test edilen alan |
| `/actuator` açığa çıkışı | Yalnızca `health,info,prometheus`; prometheus üretimde 302 → korumalı (test edildi) |
| Oturum çerezi | `secure`, `http-only`, `same-site=lax` açıkça tanımlı |
| Güvenlik başlıkları | CSP, HSTS, X-Frame-Options DENY, Referrer-Policy mevcut |
| Public randevuda betik enjeksiyonu | Sunucu tarafında reddediliyor + testi var (`UiWorkflowIT`) |
| Bot koruması | Honeypot + form doldurma süresi + IP başına günlük sınır |
| SQL enjeksiyonu | JPA/Hibernate; ham SQL birleştirmesi yok |

---

## A. Güvenlik

### A1. 🔴 Çıktı kaçışı yok — depolanmış XSS

**Bulgu:** Arayüz sunucudan gelen veriyi `innerHTML` içine **kaçışsız** basıyor.
Örnekler:

- `calendar.js:42` → `<option ...>${s.name}</option>` (personel adı)
- `calendar.js:55` → hizmet adı
- `calendar.js:169,178` → `${a.customerName}` (randevu sahibi)
- `app.js:36` → `showToast` mesajı

Kaçış fonksiyonu (`escapeHtml`) **zaten var** (`app.js:283`) ama bu yollarda
kullanılmıyor.

**Neden bugün patlamıyor:** Public randevu ucu betik içeren müşteri adını 400 ile
reddediyor. Yani tek bir *girdi* kapısı tutuluyor.

**Neden yine de açık:** Koruma girdi tarafında ve yalnızca o alanda. Panelden
randevu açan bir resepsiyonist, personel adı, hizmet adı ve müşteri adı alanlarına
istediğini yazabiliyor; hiçbirinde bu kontrol yok. Hizmet adları ayrıca **herkese
açık randevu sayfasında** çiziliyor.

Tek bir alanı girdi tarafında filtreleyip çıktıyı hiç kaçırmamak, "her girdi
kapısını sonsuza dek doğru tutmak" varsayımına dayanıyor. Sürdürülebilir değil.

**Yapılacak:** Sunucudan gelen her değeri `innerHTML` yoluna girerken `escapeHtml`
ile geçir. Öncelik sırası: `calendar.js`, `booking.js`, `customers.js`, `app.js`.

---

### A2. Yetki matrisi neredeyse test edilmemiş

**Bulgu:** `SecurityConfig` 27 rol kuralı tanımlıyor (`hasAnyRole(MGMT)` 9,
`MGMT_RECEPTIONIST` 7, `STAFF_READ` 5, …). Test tarafında **403 bekleyen yalnızca
5 test** var.

Yani "resepsiyonist `/api/users`'a girebiliyor mu", "uzman `/api/expenses`'i
görebiliyor mu" gibi sorular sistematik olarak sorulmuyor. Bir rol kuralı yanlış
yazılsa ya da yeni bir uç eklenirken unutulsa, testler sessiz kalır.

**Yapılacak:** Rol × uç matrisini parametreli tek bir testle tara. Her hücre için
beklenen sonucu (200 / 403) yaz. Yeni uç eklendiğinde matris de büyür.

---

### A3. Hız sınırı yalnızca 4 uçta

**Bulgu:** `RateLimitFilter.limitFor()` yalnızca giriş, kayıt, OTP ve randevu
uçlarına sınır koyuyor; geri kalan her şey `return 0` — yani **sınırsız**.

Kimliği doğrulanmış bir kullanıcı (ya da ele geçirilmiş bir oturum) herhangi bir
API'yi sınırsız dövebilir. 2 vCPU / 2.9 GB RAM'lik, 12 konteyner paylaşan bir
makinede bu doğrudan servis kesintisi demek.

**Yapılacak:** Kimlikli API'ler için makul bir genel taban sınır (örn. kullanıcı
başına 120 istek/dk). Raporlama/dışa aktarma gibi pahalı uçlara daha dar sınır.

---

### A4. CI'da bağımlılık taraması yok

**Bulgu:** `pom.xml` ve workflow'larda `dependency-check`, `snyk`, `trivy`,
`codeql` — hiçbiri yok. Spring Boot 3.4.5 kullanılıyor; bilinen bir CVE çıktığında
bunu öğrenmenin bir yolu yok.

**Yapılacak:** GitHub'ın **Dependabot** ve **CodeQL** özelliklerini aç (ücretsiz,
public repo). Ek olarak imaj taraması için Trivy adımı — build zaten CI'a taşınacak.

---

### A5. Origin sunucu Cloudflare'siz de erişilebilir

**Bulgu:** Zincir `istemci → Cloudflare → nginx → uygulama`. nginx hangi
adresten bağlanıldığına bakmadan çalışıyor; yani sunucunun gerçek IP'sini bilen
biri Cloudflare'i atlayıp doğrudan nginx'e bağlanabilir.

**Neden önemli:** Uygulama, loopback'ten gelen isteklerde `CF-Connecting-IP` ve
`X-Forwarded-For` başlıklarına itibar ediyor — etmek zorunda, çünkü gerçek
istemci adresini başka türlü öğrenemez. Cloudflare üzerinden gelen istekte bu
başlıkları Cloudflare yazar ve istemcininkini ezer, yani sahtelenemez. Ama
doğrudan nginx'e bağlanan biri başlığı kendisi uydurur ve her istekte farklı bir
adres göndererek **giriş, kayıt, OTP, randevu yazma ve günlük randevu
sayaçlarının hepsini** atlar.

Uygulama tarafında yapılabilecek her şey yapıldı (bkz. `ClientIpResolver`);
kalan kısım yalnızca sunucuda çözülebilir.

**Yapılacak (SSH):** nginx yalnızca Cloudflare adres bloklarından bağlantı
kabul etsin. İki yol var, ikisi de sunucuda:

1. `ngx_http_realip_module` + Cloudflare IP listesi — hem `$remote_addr` gerçek
   istemci olur hem de listede olmayan kaynak reddedilebilir.
2. Ya da güvenlik duvarında (ufw) 443'ü yalnızca Cloudflare bloklarına açmak.

Cloudflare listesi: `https://www.cloudflare.com/ips-v4` ve `.../ips-v6`.
Liste değiştiğinde güncellenmesi gerekir; bu yüzden düzenli bir işe bağlanmalı.

**Aciliyet:** Orta. İstismarı için önce origin IP'sinin bulunması gerekiyor.
Ama bulunması zor değil (eski DNS kayıtları, sertifika şeffaflık günlükleri) ve
bulunduğunda hız sınırlarının tamamı devre dışı kalıyor.

---

## B. Yük ve performans

### B1. Hiç yük testi yapılmadı

Sistem hiç yük altında görülmedi. Bilinmeyenler: eşzamanlı kaç randevu isteği
kaldırıyor, takvim sorgusu kaç randevuda yavaşlıyor, WebSocket kaç bağlantıda
tıkanıyor.

**Bilinen darboğaz adayları:**

| Yer | Ayar | Risk |
|---|---|---|
| DB bağlantı havuzu | `maximum-pool-size: 10` (`application-prod.yml:7`) | 10 eşzamanlı sorgu üstünde istekler kuyruğa girer |
| JVM heap | 512 MB (bu gece kondu) | Daralırsa `ExitOnOutOfMemoryError` → konteyner ölür |
| Uygulama örneği | 1 adet | Tek nokta |
| Makine | 2.9 GB RAM, 12 konteyner, swap'in 1.1 GB'ı kullanımda | Komşu servisler de yarışıyor |

**Yapılacak:** Gerçekçi bir senaryoyla ölçüm — 20 eşzamanlı ziyaretçi randevu
sayfasında + 3 personel panelde. Ölçülecek: p95 yanıt süresi, hata oranı, heap ve
RSS eğrisi, havuz doygunluğu. **Üretimde değil**, önce yerelde; üretimde ancak
düşük yoğunlukla ve haber vererek.

### B2. Bellek limitinin doğrulanması

Bu gece `mem_limit: 1g` + 512 MB heap canlıya alındı ama JVM'in limiti gördüğü
ölçülmedi. Adım `docs/ssh-gorev-3.md` İş 4'te.

### B3. İndeks ve sorgu kontrolü

Takvim ve dashboard sorgularının indeks kullanıp kullanmadığı hiç bakılmadı.
Randevu tablosu büyüdükçe (salon × gün × personel) ilk yavaşlayacak yer burası.

---

## C. Kriz hazırlığı

### C1. 🔴 İzleme ve uyarı yok

**Bulgu:** Workflow'larda ve deploy dokümanlarında hiçbir uyarı mekanizması yok.
Prometheus ucu açık ama onu toplayan bir şey yok.

**Sonuç:** Üretim gece 03:00'te düşerse, bunu **müşteri söyleyene kadar kimse
bilmez.** Kriz önlemenin en ucuz adımı bu.

**Yapılacak:** Dışarıdan basit bir uptime kontrolü — `/actuator/health` her
dakika, düşerse e-posta/Telegram. Ücretsiz katmanlar bu iş için fazlasıyla yeterli.
Kurulumu dakikalar sürer, hiçbir kod değişikliği gerektirmez.

### C2. Yedeğin geri yüklenebildiği kanıtlanmadı

`docs/ssh-gorev-3.md` İş 1. Yedek almak ile geri yükleyebilmek aynı şey değil.

### C3. Olay anında ne yapılacağı yazılı değil

Geri alma yolu var (`geri_al`) ama "site düştü, sırayla şunlara bak" diye bir not
yok. Kriz anında bu, kaybedilen zaman demek.

---

## Öncelik sırası

| # | Madde | Neden bu sırada | Kim |
|---|---|---|---|
| 1 | **C1 — uptime uyarısı** | En ucuz, en yüksek kazanç: sorunu müşteriden önce öğrenmek | Kullanıcı (hesap açma) |
| 2 | **A1 — çıktı kaçışı** | Gerçek, doğrulanmış açık; sınırlı ve net bir düzeltme | Ben |
| 3 | **A2 — yetki matrisi testi** | 27 kural, 5 test. Sessiz regresyon riski | Ben |
| 4 | **C2 — geri yükleme provası** | Yedek kanıtlanmadan yedek sayılmaz | SSH |
| 5 | **A3 — hız sınırı kapsamı** | Kaynak kısıtlı makinede servis kesintisi riski | Ben |
| 6 | **A4 — Dependabot + CodeQL** | Ücretsiz, tek seferlik kurulum | Kullanıcı (repo ayarı) |
| 7 | **B1 — yük testi** | Darboğazı ölçmeden büyütmek körlemesine | Ben |
| 8 | **C3 — olay kılavuzu** | Yukarıdakiler bittiğinde yazılır | Ben |
| 9 | **B3 — indeks kontrolü** | Veri büyüyünce önem kazanır | Ben |

İki konteynerli kuruluma geçiş (Aşama 3) bu listenin **altında** — bellek
davranışı birkaç gün gözlenmeden ikinci örnek açmak ölçüsüz olur.
