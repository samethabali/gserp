# SSH görevi #2 — yapılanlar raporu

**Görev kaynağı:** [docs/ssh-gorev-2.md](ssh-gorev-2.md)
**Önceki rapor:** [docs/ssh-yapılanlar.md](ssh-yapılanlar.md) (salt okuma toplama)
**Tarih:** 2026-09-05 (sunucu saati 14:14–14:16)
**Dal:** `staging` (`8b3f375` üzerinde)
**Sunucu:** `avesitesi` — SSH `emre`, yazma işleri `ssh -i /home/emre/gserp_deploy gserp@localhost` ile
**Sonuç:** Üç işin **üçü de tamamlandı**. Kesinti olmadı.

> **Kesinti kanıtı:** `gserp-app` `StartedAt = 2026-09-05T10:26:26Z` (görev öncesiyle aynı),
> `RestartCount = 0`, görev sonunda `HTTP 200` / `{"status":"UP"}`. Konteyner yeniden
> başlatılmadı, 12 konteynerin hiçbirine dokunulmadı.

---

## 1. Özet — istenen beş rapor maddesi

| # | Soru | Cevap |
|---|---|---|
| 1 | Yedek script'i kuruldu ve **elle doğrulandı** mı? | **Evet.** İki kez çalıştırıldı, `gunzip -t` geçti, 42 tablo + 42 `COPY` bloğu doğrulandı. Dosya boyutu **301.347 bayt**. |
| 2 | Cron satırı ve hangi kullanıcı? | `0 4 * * *`, **`gserp`** kullanıcısının crontab'ında (öncesinde crontab yoktu). |
| 3 | `builder prune` öncesi/sonrası boş disk | **4.6 GB → 8.2 GB** (%84 → %71). Temizlenen: 3.901 GB. |
| 4 | Compose dosyaları aynı mıydı, taşındı mı? | **Bayt bayt aynıydı** (sha256 `798c45a1…`). Taşındı, iki yedek kopya bırakıldı. |
| 5 | Başka bir şey değişti mi? | **Hayır.** Tam liste §6'da. |

**Görev dosyasında yazmayan bir yan etki tespit edildi — §7.** Kısaca: şu anda
`production-ready`'ye deploy tetiklenirse **başarısız olur**. Kesinti yaratmaz ama
doğru sıra "önce `staging` → `production-ready` merge, sonra deploy".

---

## 2. İş 1 — Veritabanı yedeklemesi

### Kurulan

```
/home/gserp/backup-gserp-db.sh    sahibi gserp:gserp, mod 700, 1352 bayt
/home/gserp/backups/              mod 700 (müşteri verisi)
```

Script `/tmp` üzerinden aktarıldı, aktarım sonrası sha256 karşılaştırıldı
(`2e5e7e42…` her iki tarafta aynı), geçici dosya silindi.

### Görevdeki taslaktan iki sapma — sebebiyle birlikte

Görev dosyasındaki taslak `#!/bin/sh` + `set -eu` ile şunu yapıyordu:

```sh
docker exec postgres pg_dump ... | gzip > "$DEST/gserp_db-$STAMP.sql.gz"
find "$DEST" -name 'gserp_db-*.sql.gz' -mtime +14 -delete
```

**Bu sessizce bozulur.** Ubuntu'da `/bin/sh` = dash ve dash'te **`pipefail` yok**;
bir pipeline'ın çıkış kodu *son* komutunkidir. `pg_dump` çökse bile `gzip` başarıyla
biter, `set -e` tetiklenmez, script 0 ile çıkar ve **hemen ardından rotasyon çalışıp
sağlam yedekleri siler**. 14 gün sonra elde hiçbir kullanılabilir yedek kalmaz ve
bunu kimse fark etmez. Sessizce bozulan bir yedekleme, olmayandan daha tehlikelidir.

Bu yüzden iki değişiklik yapıldı:

1. **`#!/bin/bash` + `set -euo pipefail`** — `pg_dump` hatası artık yutulmuyor.
2. **Önce `.tmp`, doğrulama, sonra `mv`** — arşiv boş değilse *ve* `gunzip -t`
   geçiyorsa adı konuyor. **Rotasyon yalnızca başarılı bir yedekten sonra çalışıyor.**

Ek olarak `/home/gserp/backups` dizini `700`'e çekildi. `/home/gserp` zaten `750`
olduğu için dışarıya açık değildi; bu ikinci bir kat.

Script'in tamamı: [docs/sunucu/backup-gserp-db.sh](sunucu/backup-gserp-db.sh) (referans kopya).

### Doğrulama — "kurdum" demeden önce yapılanlar

| Çalıştırma | Komut | Sonuç |
|---|---|---|
| 1. Elle | `/home/gserp/backup-gserp-db.sh` | `gserp_db-20260905-1414.sql.gz` — **301.347 bayt**, çıkış kodu **0** |
| 2. Cron benzeri | `env -i PATH=/usr/local/bin:/usr/bin:/bin HOME=/home/gserp /bin/sh -c "…"` | `gserp_db-20260905-1415.sql.gz` — 301.343 bayt, çıkış kodu **0**, `backup.log`'a yazdı |

İkinci çalıştırma özellikle `env -i` ile boş bir ortamda yapıldı: cron'un dar
`PATH`'inde `docker` bulunamaması gibi klasik bir kurulum hatasının sızmadığını
görmek için. `crontab -l`'e bakıp "kuruldu" denmedi.

Arşiv sağlaması:

```
gunzip -t                 -> ARSIV SAGLAM
açılmış boyut             -> 845.165 bayt (0.8 MB)
satır sayısı              -> 8.705
^CREATE TABLE sayısı      -> 42
^COPY  bloğu sayısı       -> 42
```

42 tablo + 42 veri bloğu → şema ve veri eksiksiz. (13 MB'lık `pg_database_size`
karşısında 845 KB'lık dump normaldir; o rakam indeksleri, boş alanı ve şişkinliği
de sayar. Davet kodları henüz kullanılmadığı için gerçek satır hacmi düşük.)

### Bağlantı bilgileri

Script `DB_USER` ve `DB_NAME`'i `.env`'den okuyor, hiçbir yere basmıyor.
`DB_PASSWORD`'a hiç dokunulmuyor: `docker exec` ile konteyner içinden yerel sokete
bağlanıldığı için postgres imajının `local all all trust` kuralı geçerli, parola
gerekmiyor. **`.env` bu oturumda okunmadı.**

---

## 3. İş 2 — Cron

`gserp` kullanıcısının crontab'ı **boştu** (`no crontab for gserp`), üzerine yazılan
bir şey olmadı:

```cron
PATH=/usr/local/bin:/usr/bin:/bin
# gserp_db gunluk yedegi — docs/ssh-gorev-2.md Is 1 (kurulum: 2026-09-05)
# 14 gun rotasyon script icinde. Cikti backups/backup.log'a eklenir.
0 4 * * * /home/gserp/backup-gserp-db.sh >> /home/gserp/backups/backup.log 2>&1
```

Saat 04:00 seçildi — deploy penceresiyle çakışmıyor. `PATH` açıkça yazıldı; gerekli
tüm komutların (`docker`, `gzip`, `gunzip`, `find`, `date`, `stat`, `grep`, `cut`,
`mkdir`, `mv`, `rm`) `/usr/bin` altında olduğu önceden doğrulandı.

`cron.service` çalışıyor (önceki envanterde teyit edilmişti).

---

## 4. İş 3 — Build cache temizliği

| | Boş alan | Doluluk |
|---|---|---|
| **Öncesi** | 4.6 GB | %84 |
| **Sonrası** | **8.2 GB** | **%71** |

```
docker builder prune -f   ->  Total: 3.901GB
```

`docker system df` değişimi:

```
                ÖNCE                        SONRA
Images          9.441GB  (239.9MB atıl)     6.856GB  (239.9MB atıl)
Containers      2.294MB                     2.294MB
Local Volumes   4.309GB  (290MB atıl)       4.309GB  (290MB atıl)   <- dokunulmadı
Build Cache     4.469GB  (3.937GB atıl)     568.3MB  (35.76MB atıl)
```

**İmaj silinmedi:** 13 imajın 13'ü, 12 konteynerin 12'si yerinde. `Images` satırındaki
düşüş imaj kaybı değil, build cache ile ortak sayılan katmanların muhasebeden
çıkmasıdır.

`docker system prune` **kullanılmadı** (öksüz volume'lere uzanırdı).
`docker volume prune` **kullanılmadı** — öksüz volume'ler arasında ölü bir sitenin
postgres verisi var (`yahyakaptan_pgdata`, 113.7 MB).

Temizlik sonrası doğrulama: 12 konteyner ayakta, `gserp-app` healthy,
`HTTP 200` / `{"status":"UP"}` (0.013 sn).

---

## 5. İş 4 — Deploy engelinin kaldırılması

### Taşımadan önce: içerik doğrulaması

Görev dosyası "taşımadan önce repodaki kopyayla aynı olduğunu doğrula" diyordu.
Doğrulandı:

```
sunucu /home/gserp/gserp/docker-compose.prod.yml
  798c45a1595daad418c0f37a736733cb5e30a01e6b051b68356634b24bfcbe08

repo   git show 445cb5a:docker-compose.prod.yml
  798c45a1595daad418c0f37a736733cb5e30a01e6b051b68356634b24bfcbe08
```

**Bayt bayt aynı** → 445cb5a'dan bu yana sunucuda elle değişiklik yapılmamış,
taşımak güvenli.

> Not: `staging`'in güncel sürümü (`8b3f375`, `mem_limit` + log rotasyonu eklenmiş)
> farklı hash taşıyor (`e9fd03c1…`) — beklenen durum, o sürüm henüz canlıya
> alınmayacak.

### Yapılan

```bash
cp -a docker-compose.prod.yml /home/gserp/docker-compose.prod.yml.sunucu-yedek
mv    docker-compose.prod.yml docker-compose.prod.yml.git-oncesi
```

Taşıma öncesi `git status --porcelain` çıktısı `?? docker-compose.prod.yml` idi
(takipsiz — beklenen). Sonrasında bu adda dosya kalmadı, iki yedek kopyanın da
hash'i orijinalle aynı:

```
798c45a1…  /home/gserp/docker-compose.prod.yml.sunucu-yedek       (repo dışı)
798c45a1…  /home/gserp/gserp/docker-compose.prod.yml.git-oncesi   (repo içi, takipsiz)
```

Artık `git merge --ff-only`, `docker-compose.prod.yml`'i yazarken takipsiz dosya
engeline takılmayacak.

**Geri alma (tek satır):**

```bash
ssh -i /home/emre/gserp_deploy gserp@localhost \
  'mv /home/gserp/gserp/docker-compose.prod.yml.git-oncesi /home/gserp/gserp/docker-compose.prod.yml'
```

---

## 6. Sunucuda değişen her şey

Aşağıdakiler dışında **hiçbir şey** değişmedi:

```
[yeni]     /home/gserp/backup-gserp-db.sh                  700, gserp:gserp
[yeni]     /home/gserp/backups/                            700
[yeni]       ├─ gserp_db-20260905-1414.sql.gz              301.347 bayt
[yeni]       ├─ gserp_db-20260905-1415.sql.gz              301.343 bayt
[yeni]       └─ backup.log                                 81 bayt
[yeni]     /home/gserp/docker-compose.prod.yml.sunucu-yedek
[taşındı]  /home/gserp/gserp/docker-compose.prod.yml -> .git-oncesi
[yeni]     gserp crontab                                   (öncesinde yoktu)
[silindi]  docker build cache                              3.901 GB
```

Uyulan yasaklar:

- ❌ `docker compose up/down/build`, `docker restart` — çalıştırılmadı
- ❌ `docker volume prune` — çalıştırılmadı
- ❌ `.env` içeriğini okumak — okunmadı (script okuyor, hiçbir yere basmıyor)
- ❌ `docker compose config` — çalıştırılmadı
- ❌ `production-ready`'ye push — yapılmadı
- ❌ Deploy tetiklemek — yapılmadı
- ❌ İkinci konteyner / nginx `upstream` — girilmedi

---

## 7. ⚠️ Görev dosyasında yazmayan yan etki

Görev dosyası şöyle diyor: *"docker-compose.prod.yml artık repoda izleniyor (staging
dalında)… git takipsiz bir dosyanın üzerine yazılmasına izin vermeyip deploy'u
durdurur."*

Bu doğru — **ama yalnızca `staging` `production-ready`'ye girdikten sonra.**
Şu anki durum:

```
origin/production-ready (75412cb)  ->  docker-compose.prod.yml  İZLEMİYOR
origin/staging          (8b3f375)  ->  docker-compose.prod.yml  İZLİYOR
```

Deploy workflow'u `production-ready`'yi merge ediyor. O dal dosyayı taşımadığı için
merge **çakışmaz** — ama hemen sonrasında şu kontrole takılır:

```bash
if [ ! -f "$COMPOSE_FILE" ]; then
  echo "[x] $COMPOSE_FILE yok — bu workflow ilk kurulumu yapmaz."
  exit 1
fi
```

**Sonuç:** Şu anda `production-ready`'ye bir deploy tetiklenirse **başarısız olur.**
Kesinti yaratmaz (hiçbir konteyner durdurulmaz, workflow build'e bile gelmeden
düşer), ama beklenmedik gelirse kafa karıştırır.

**Doğru sıra:**

1. `staging` → `production-ready` merge (tracked `docker-compose.prod.yml` gelir)
2. Sonra deploy

Bu merge yapıldığında `8b3f375`'teki `mem_limit` + log rotasyonu ayarları da canlıya
iner — görev dosyasının "gece penceresinde, kullanıcıyla birlikte" dediği karar
budur. Deploy'un kendisi ~100 sn kesinti demek.

Acil bir deploy gerekirse, o merge yapılana kadar geçici çözüm §5'teki geri alma
satırıdır.

---

## 8. Bundan sonrası

[docs/YAPILACAKLAR.md](YAPILACAKLAR.md) üzerinden:

- ✅ **Yedekleme** — kuruldu ve doğrulandı. Önceki raporun (§9, 0. madde) en acil
  kalemi kapandı.
- ✅ **Disk** — 8.2 GB boş. İmaj build'i hâlâ bu kutuda yapılıyor, yani bu kalıcı
  bir çözüm değil; Aşama 1 (build'i CI'a taşı) hâlâ geçerli.
- ✅ **Deploy engeli** — kaldırıldı, §7'deki sıraya dikkat.
- ⬜ **`mem_limit` + `JAVA_OPTS`** — `staging`'de hazır (`8b3f375`), canlıya
  inmedi. Gerekçe ve beklenen kazanç: [ssh-yapılanlar.md §5b](ssh-yapılanlar.md).
- ⬜ **Log rotasyonu (host tarafı)** — Docker tarafı `8b3f375`'te çözüldü ama
  `journald` hâlâ sınırsız (209 MB). `SystemMaxUse=200M` ayrı bir iş.
- ⬜ **6 ölü nginx sitesi** — bkz. [envanter.txt §5](sunucu/envanter.txt).

### İlk cron çalıştırmasını doğrula

Yedekleme yarın 04:00'te ilk kez cron üzerinden çalışacak. Sonrasında:

```bash
ssh -i /home/emre/gserp_deploy gserp@localhost 'cat /home/gserp/backups/backup.log; ls -la /home/gserp/backups/'
```

`backup.log`'da yeni bir `gserp-backup OK:` satırı ve o tarihli bir `.sql.gz`
görülmeli. Elle ve `env -i` ile çalıştığı doğrulandı, ama gerçek cron altında ilk
turu görmek kurulumu kesinleştirir.

### Geri yükleme (test edilmedi)

Yedeğin geri yüklenebildiği **denenmedi** — canlı veritabanına yazmak bu görevin
kapsamı dışındaydı. Komut şu olurdu:

```bash
gunzip -c /home/gserp/backups/gserp_db-YYYYMMDD-HHMM.sql.gz \
  | docker exec -i postgres psql -U gserp_user -d <bos_test_db>
```

Gerçek bir güven için bunun bir kez boş bir test veritabanına karşı denenmesi
gerekir. Yedek almak ile geri yükleyebilmek aynı şey değildir.
