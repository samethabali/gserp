# Yük testi — ilk ölçüm ve bulunan darboğazlar

**Tarih:** 2026-09-05
**Test:** `BookingLoadIT` — 20 eşzamanlı ziyaretçi randevu sayfasında + 3 personel
panelde, 30 saniye, dolu bir gün (40 randevu, 4 uzman, 4 hizmet).
**Ortam:** yerel makine, Testcontainers PostgreSQL 16. **Üretimde ölçüm yapılmadı.**

Rakamlar makineye bağlı; anlamlı olan **önce/sonra oranı** ve **istek başına SQL
sayısı**. İkincisi makineden bağımsızdır.

Çalıştırmak için:

```bash
mvn test -Dsurefire.excludedGroups= -Dtest=BookingLoadIT
```

Varsayılan koşuda çalışmaz (`load` etiketi dışlanır): paylaşımlı bir CI
runner'ında ölçülen gecikme makinenin o anki yüküne bağlıdır ve böyle bir testi
kırmızı/yeşil kapısı yapmak, ürün hatası olmadığı hâlde düzenli kırılan bir test
demektir.

---

## Bulunan iki darboğaz

İkisi de aynı sınıftan: **istek başına sorgu sayısı veri miktarıyla doğru
orantılı büyüyordu.** Bu, az veriyle hiç görünmeyen, veri büyüdükçe aniden
çöken bir hata türü — yani tam olarak pilot sonrası patlayacak cinsten.

### 1. Müsait saatler ucu: slot başına bir çakışma sorgusu

`AvailabilityService.slotsFor` ürettiği **her slot** için ayrı bir
"bu uzman bu aralıkta müsait mi" sorgusu atıyordu. 09:00–19:00 mesaisi ve 15
dakikalık adımla bu 39 slot, yani 39 SQL demek.

Bu, herkese açık randevu sayfasının **en sıcak yolu**: ziyaretçi her gün
seçtiğinde çağrılıyor.

**Düzeltme:** günün dolu blokları tek sorguyla alınıyor, çakışma kontrolü
bellekte yapılıyor (`SchedulerService.busyBlocks` + `isFree`). Çakışma kuralı
tek yerde kaldı — `isStaffAvailable` de aynı iki metodu kullanıyor, yani
paralel bir implementasyon oluşmadı.

### 2. `Appointment` üç EAGER koleksiyon taşıyor

`resourceIds`, `bodyRegions` ve `flags` `FetchType.EAGER`. Hibernate birden çok
koleksiyonu tek sorguda birleştiremediği için **her randevu üç ek SELECT**
demekti: takvimin bir günü 40 randevuysa 120 fazladan sorgu.

Bu yalnızca takvimi değil, `Appointment` yükleyen **her yolu** etkiliyordu.

**Düzeltme:** üç koleksiyona da `@BatchSize(size = 100)`. Koleksiyonlar artık
randevu başına değil, yüzerlik gruplar hâlinde
`where appointment_id in (...)` ile yükleniyor. Üç tablonun da
`appointment_id` ile başlayan bir indeksi/birincil anahtarı zaten var.

EAGER'dan LAZY'ye çevirmek daha fazla sorgu tasarruf ederdi ama işlem sınırı
dışında koleksiyona dokunan çağrı yerlerini kırma riski taşıyordu; `@BatchSize`
aynı kazancın büyük kısmını sıfır davranış değişikliğiyle veriyor.

---

## Ölçüm

### İstek başına SQL

| Uç | Önce | Sonra |
|---|---:|---:|
| Randevu sayfası (HTML) | 2 | 2 |
| `GET /api/settings/public` | 5 | 5 |
| `GET /api/booking/services` | 6 | 6 |
| `GET /api/booking/staff` | 6 | 6 |
| **`GET /api/booking/availability`** | **79** | **14** |

### Yanıt süreleri (20 ziyaretçi + 3 personel, 30 sn)

| İstek | p50 önce | p50 sonra | p95 önce | p95 sonra |
|---|---:|---:|---:|---:|
| Panel: takvim | 844 ms | **124 ms** | 1309 ms | **264 ms** |
| Panel: bugün | 215 ms | **81 ms** | 447 ms | **182 ms** |
| Ziyaretçi: sayfa | 200 ms | **27 ms** | 1554 ms | **112 ms** |
| Ziyaretçi: ayarlar | 173 ms | **50 ms** | 348 ms | **138 ms** |
| Ziyaretçi: hizmetler | 162 ms | **45 ms** | 336 ms | **151 ms** |
| Ziyaretçi: personel | 157 ms | **44 ms** | 497 ms | **168 ms** |
| Ziyaretçi: müsait saatler | 648 ms | **102 ms** | 1139 ms | **222 ms** |

**Toplam iş:** 30 saniyede 1433 istek → **3969 istek**. 46,9 istek/sn → **131,1
istek/sn**. Hata oranı iki ölçümde de **sıfır**.

Yani aynı donanım, aynı süre, aynı senaryo — yaklaşık **2,8 kat iş**, yanıt
süreleri **5–7 kat** daha kısa.

---

## Regresyon koruması

Yük testi kapıda duramaz (yavaş ve makineye bağlı). Onun yerine
`QueryScalingIT` CI'da her koşuda çalışıyor ve **sabit bir eşik değil değişmez**
ölçüyor:

- Müsait saatler ucu: 2 saatlik mesai ile 10 saatlik mesai **aynı** sayıda sorgu
  üretmeli → slot başına sorgu geri gelirse test kırılır.
- Takvim ucu: 3 randevulu gün ile 40 randevulu gün **aynı** sayıda sorgu
  üretmeli → randevu başına sorgu geri gelirse test kırılır.

Sabit sayı yazsaydık, ilgisiz bir yerde eklenen tek bir sorgu testi kırardı.

---

## Havuz hakkında

Her iki ölçümde de en yüksek değerler aynı: **10 aktif bağlantı, 13 bekleyen
istek** (havuz boyutu 10). Yani havuz iki durumda da doluyor.

Fark şurada: düzeltmeden sonra her istek bağlantıyı **çok daha kısa** tutuyor,
bu yüzden kuyruk hızla boşalıyor. Havuzu büyütmek doğru refleks değildi —
sorunu sorgu sayısı yaratıyordu, bağlantı sayısı değil. Havuzu 10'da bırakmak
ayrıca doğru: veritabanı aynı makinede 11 başka konteynerle paylaşılıyor.

---

## Kapsam dışı kalanlar

- **Randevu yazma yükü.** `POST /api/booking/request` IP başına 10/dk ile
  sınırlı; tek makineden gelen yük bunu anlamlı ölçemez.
- **WebSocket.** Kaç eşzamanlı bağlantıda tıkandığı ölçülmedi.
- **Uzun vadeli veri büyümesi.** Ölçüm tek günlük 40 randevuyla yapıldı;
  randevu tablosu yıllar içinde büyüdüğünde indeks davranışı yeniden bakılmalı.
- **Üretim ölçümü.** Bu rakamlar yerel makineden. Üretimde ölçüm ancak düşük
  yoğunlukla ve haber vererek yapılmalı.

---

## Yan bulgu: statik dosyalar hiç önbelleklenmiyordu

Ölçüm sırasında ortaya çıktı ve yük testinden bağımsız olarak düzeltildi.

Üretimde CSS/JS yanıtları `Cache-Control: no-cache, no-store, max-age=0,
must-revalidate` taşıyordu — bu başlığı kimse yazmamıştı, boşluğu Spring
Security'nin varsayılanı dolduruyordu. Sonucu:

- Tarayıcı her sayfa geçişinde **bütün** CSS/JS'i yeniden indiriyordu (koşullu
  istek bile değil, tam indirme).
- Cloudflare de önbellekleyemiyordu: `cf-cache-status: BYPASS`. Yani her
  kullanıcının her statik dosya isteği Türkiye'deki origin sunucuya kadar
  gidiyordu.

**Düzeltme:** `/js/**` ve `/css/**` için yedi günlük `public` önbellek
(`WebConfig`). Bunu güvenli kılan şart adresin sürüm taşıması; sayfaların
çoğunda düz `src="/js/x.js"` yazılıydı, hepsi sürümlü hâle getirildi ve sürüm
artık **her derlemede** otomatik değişiyor (`pom.xml → build.timestamp`), elle
güncellenen bir numara değil. `UiPageRenderIT` sürümsüz bir adres kalırsa
testi kırıyor.
