# Olay kılavuzu — bir şeyler bozulduğunda

**Amaç:** Kriz anında ne yapacağını düşünmek zorunda kalmamak. Sırayla uygula.

Bu belge tahmin içermez; her adım daha önce bu sistemde yapılmış ya da
doğrulanmış bir işlemdir.

---

## 0. Önce şunu bil

| Gerçek | Sonucu |
|---|---|
| Oturumlar bellekte tutuluyor | Uygulama her yeniden başladığında **herkes çıkış yapmış olur** |
| Deploy ~100 saniye kesinti demek | "Hızlıca bir deneyelim" diye deploy atma; her deneme 100 sn kesinti |
| Sunucuda 12 konteyner var, 2 vCPU / 2.9 GB RAM | Sorun gserp'te değil, komşu bir serviste de olabilir |
| TLS ve önbellek Cloudflare'de | Site açılmıyorsa hata Cloudflare'de de olabilir; önce origin'i test et |
| Yedek her gece 04:00'te alınıyor | En kötü senaryoda kaybedilecek veri: son yedekten bu yana geçen süre |

---

## 1. İlk 60 saniye — teşhis

Sırayla, hiçbir şeyi değiştirmeden:

```bash
# 1. Uygulama ayakta mı? (Cloudflare üzerinden)
curl -sS -o /dev/null -w "%{http_code} %{time_total}s\n" https://gscrm.avesitesi.xyz/actuator/health

# 2. Cloudflare mı origin mi? Server ve CF-RAY başlıklarına bak.
curl -sS -D - -o /dev/null https://gscrm.avesitesi.xyz/actuator/health | head -20
```

| Gördüğün | Anlamı | Git |
|---|---|---|
| `200 {"status":"UP"}` | Uygulama sağlıklı — sorun başka yerde | [§5 Yavaşlık](#5-site-açık-ama-yavaş) |
| `502` / `503` | Uygulama ayakta değil ya da yanıt vermiyor | [§2](#2-uygulama-ayakta-değil) |
| `521` / `522` / `523` | Cloudflare origin'e ulaşamıyor — sunucu ya da ağ | [§2](#2-uygulama-ayakta-değil) |
| `429` | Hız sınırı — muhtemelen bir istemci döngüsü | [§6](#6-429-çok-fazla-deneme) |
| Hiç yanıt yok | DNS ya da Cloudflare | Cloudflare panelini kontrol et |

**Sunucunun durumunu değiştirmeden görmek için:** GitHub → Actions → *Deploy to VPS*
→ *Run workflow* → **`sadece_teshis` işaretli**. Konteyner durumu, disk, RAM ve son
loglar rapor edilir; hiçbir şey değiştirilmez.

---

## 2. Uygulama ayakta değil

### 2a. Son deploy'dan sonra mı bozuldu?

GitHub → Actions → son *Deploy to VPS* koşusunun zamanına bak.

**Evet, son deploy'dan sonra bozuldu → GERİ AL.** Tartışma yok, önce sistemi
ayağa kaldır, sebebi sonra ara:

> GitHub → Actions → **Deploy to VPS** → Run workflow → **`geri_al` işaretle** → Run

Bu, bir önceki imaja döner. **Build yapmaz**, ~100 saniye sürer.

Geri aldıktan sonra doğrula:

```bash
curl -sS https://gscrm.avesitesi.xyz/actuator/health
```

### 2b. Deploy ile ilgisi yok

Muhtemel sebepler, en olasıdan başlayarak:

1. **Bellek** — konteyner limiti 1 GB, JVM `-XX:+ExitOnOutOfMemoryError` ile
   açık: bellek biterse konteyner kendini öldürür. Makine zaten swap kullanıyor.
   `sadece_teshis` raporunda RAM ve konteynerin restart sayısına bak.
2. **Veritabanı** — `postgres` konteyneri ayakta mı? gserp ona `shared-db`
   ağı üzerinden bağlanıyor.
3. **Disk** — 30 GB tek bölüm. Loglar sınırlı (`max-size: 10m`, 3 dosya) ama
   yedekler ve komşu servisler doldurabilir.
4. **Komşu servis** — aynı makinede 11 başka konteyner var. Biri belleği
   tüketmişse gserp de düşer.

---

## 3. Geri alma yeterli olmadı

Uygulama geri alındıktan sonra da açılmıyorsa sorun kodda değil; ortamda.
Bu noktada SSH erişimi olan bilgisayardan yardım iste ve şunları söyle:

- `docker ps -a` — hangi konteyner ayakta, hangisi restart döngüsünde
- `docker logs --tail 200 gserp-app` — uygulamanın son sözü
- `free -m` ve `df -h` — bellek ve disk
- `docker stats --no-stream` — hangi konteyner kaynağı yiyor

**Yapılmayacaklar** (her biri daha önce zarar verebilecek durumdaydı):

- ❌ `docker compose config` çalıştırma — `.env` içindeki sırları düz metin basar
- ❌ `docker volume prune` / `docker system prune` — sahipsiz görünen
  `yahyakaptan_pgdata` başka bir sitenin veritabanı
- ❌ SSH makinesinden `production-ready` dalına push — otomatik deploy tetikler
- ❌ Gereksiz `docker restart` — her biri tüm oturumları düşürür

---

## 4. Veri kaybı şüphesi

Yedekler her gece **04:00**'te alınıyor (`backup-gserp-db.sh`, sıkıştırılmış ve
`gunzip -t` ile doğrulanmış).

**Önce dur.** Veritabanına yazmaya devam eden bir uygulamayla geri yükleme
yapılmaz. Sırayla:

1. Ne kaybedildiğini yaz: hangi tablo, hangi zaman aralığı.
2. En son geçerli yedeği bul.
3. Yedeği **geçici bir veritabanına** yükle, doğru veriyi orada gör.
4. Ancak ondan sonra canlıya dokunmayı konuş.

Doğrudan canlının üstüne geri yükleme, sorunu büyütmenin en hızlı yoludur.

---

## 5. Site açık ama yavaş

Ölçüm yapmadan iyileştirme yapma. Sırayla:

```bash
# Gerçek sunucu süresi: bağlantı kurulumunu dışarıda bırakmak için aynı
# bağlantıda birkaç istek at. İlk istek her zaman yavaştır (DNS + TLS).
# Her URL için ayrı bir -o gerekiyor, yoksa gövdeler ekrana dökülür.
curl -sS -w "%{time_starttransfer}\n" \
     -o /dev/null https://gscrm.avesitesi.xyz/actuator/health \
     -o /dev/null https://gscrm.avesitesi.xyz/actuator/health \
     -o /dev/null https://gscrm.avesitesi.xyz/actuator/health
```

**Son** istek ~250 ms civarındaysa sunucu normal çalışıyor demektir (bunun
~180 ms'i Türkiye–Frankfurt gidiş-dönüş süresi). İlk istek bağlantı kurulumunu
da içerdiği için 1,5 saniyeye kadar çıkabilir; bu normaldir.

**Statik dosyalar Cloudflare önbelleğinden geliyor mu?**

```bash
curl -sS -D - -o /dev/null https://gscrm.avesitesi.xyz/js/calendar.js | grep -i "cf-cache-status\|cache-control"
```

`HIT` olmalı. `BYPASS` görüyorsan önbellek başlıkları bozulmuş demektir; her
kullanıcı her sayfa geçişinde bütün JS/CSS'i sunucudan indiriyor.

**Uygulama gerçekten yavaşsa** en olası sebep DB bağlantı havuzunun doyması
(havuz 10). Bunun imzası: yanıt süreleri hep birlikte artar, hata oranı düşük
kalır. Ölçüm için `BookingLoadIT` (bkz. `docs/yuk-testi-sonuclari.md`).

---

## 6. 429 "Çok fazla deneme"

Kullanıcı normal kullanımda bu ekranı görmemeli. Görüyorsa ya bir istemci
döngüye girmiştir ya da sınır yanlış kurulmuştur.

Loglarda ara: `Hız sınırı aşıldı: <METOD> <yol>`. Yol hangisiyse sorun orada.

Sınırlar (`RateLimitFilter`):

| Uç | Sınır | Sayaç neye bağlı |
|---|---|---|
| Giriş / kayıt | 8 / 5 dk⁻¹ | IP |
| OTP başlat / doğrula | 3 / 6 dk⁻¹ | IP |
| Randevu oluşturma (POST) | 10 dk⁻¹ | IP |
| Randevu sayfası okumaları | 120 dk⁻¹ | Ziyaretçi (oturum) |
| Diğer tüm API | 300 dk⁻¹ | Ziyaretçi (oturum) |

---

## 7. Müşteriye ne söylenir

- **Kısa kesinti (<5 dk):** bir şey söylemeye gerek yok.
- **Uzun kesinti:** ne olduğunu değil, **ne zaman düzeleceğini** söyle. Tahmin
  veremiyorsan "bakıyoruz, en geç X'te tekrar haber vereceğim" de ve o saatte
  gerçekten haber ver.
- **Veri kaybı:** hangi verinin etkilendiğini net söyle. Belirsiz bırakma.

---

## 8. Olay bittikten sonra

Aynı olayın ikinci kez yaşanmaması için, olay tazeyken 10 dakika ayır:

1. Ne oldu, ne zaman fark edildi, ne zaman düzeldi?
2. **Nasıl fark edildi?** Müşteri söylediyse, izleme eksikliği ayrı bir sorundur.
3. Hangi test bunu yakalayabilirdi? Yazılabiliyorsa yaz.
4. Bu belgeye eklenmesi gereken bir adım çıktı mı?
