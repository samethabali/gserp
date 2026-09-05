# SSH görevi #3 — yedeği kanıtla, dışarı kopyala, log sınırı koy

SSH erişimi olan makinedeki asistan için. Önceki iki görevin devamı.

- Görev #1 (salt okuma): [`docs/ssh-yapılanlar.md`](ssh-yapılanlar.md)
- Görev #2 (yedek + disk + deploy engeli): [`docs/ssh-gorev-2-yapilanlar.md`](ssh-gorev-2-yapilanlar.md)

---

## Durum

- Üretim **yeni sürümle çalışıyor** (`81f609d`, deploy 2026-09-05 16:45 UTC).
  Konteynerde artık `mem_limit: 1g` ve JVM heap tavanı 512 MB var.
- Deploy'a **geri alma yolu** eklendi ve ilk geri dönüş noktası oluştu
  (`gserp:previous`).
- Günlük yedek kuruldu, elle doğrulandı. **Ama geri yüklenebildiği denenmedi.**
- Davet kodları gönderildi, henüz kullanılmadı.

## Kesin kurallar

| Yasak | Neden |
|---|---|
| `gserp_db` veritabanına **yazmak** | Canlı müşteri veritabanı |
| `docker compose up/down/build`, `docker restart` | Kesinti |
| `docker volume prune` / `system prune` | Ölü sitenin verisi öksüz volume'lerde |
| `.env` içeriğini paylaşmak | Sırlar |
| `production-ready`'ye push | Deploy tetikler |

---

## İş 1 — 🔴 Geri yükleme provası (en öncelikli)

Yedek alınıyor ama **geri yüklenebildiği bilinmiyor.** Yedek almak ile geri
yükleyebilmek aynı şey değil; kanıtlanmamış bir yedek, yedek sayılmaz.

**Canlı veritabanına dokunmadan**, ayrı ve geçici bir veritabanına geri yükle:

```bash
# 1) Geçici hedef — ADI GSERP_DB DEĞİL, dikkat
docker exec postgres createdb -U gserp_user gserp_restore_test

# 2) En son yedeği geri yükle
LATEST=$(ls -1t /home/gserp/backups/gserp_db-*.sql.gz | head -1)
gunzip -c "$LATEST" | docker exec -i postgres psql -U gserp_user -d gserp_restore_test

# 3) Karşılaştır — canlıdan SADECE OKUMA
for DB in gserp_db gserp_restore_test; do
  echo "== $DB"
  docker exec postgres psql -U gserp_user -d "$DB" -t -c \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"
  docker exec postgres psql -U gserp_user -d "$DB" -t -c \
    "SELECT 'salon', count(*) FROM salon UNION ALL
     SELECT 'users', count(*) FROM users UNION ALL
     SELECT 'appointment', count(*) FROM appointment UNION ALL
     SELECT 'invite_code', count(*) FROM invite_code ORDER BY 1;"
done

# 4) Flyway şema sürümü de eşleşmeli
docker exec postgres psql -U gserp_user -d gserp_restore_test -t -c \
  "SELECT max(version) FROM flyway_schema_history;"

# 5) TEMİZLİK — bunu atlama, disk ve karışıklık yaratır
docker exec postgres dropdb -U gserp_user gserp_restore_test
```

**Rapor et:** tablo sayıları ve satır sayıları eşleşti mi, Flyway sürümü ne,
geri yükleme sırasında hata çıktı mı, geçici veritabanı silindi mi.

> Sayılar birebir eşleşmeyebilir — yedek alındığından bu yana canlıya kayıt
> girmiş olabilir. Önemli olan **şemanın eksiksiz** gelmesi ve satır sayılarının
> yedek anındaki durumla tutarlı olması.

---

## İş 2 — Yedeği sunucu dışına çıkar

Yedekler şu an veritabanıyla **aynı diskte**. Disk arızasında ikisi birden gider;
bu bir yedekleme değil, bir kopya.

En az bir kopya başka bir yerde olmalı. Sırayla değerlendir, ilk uygulanabiliri seç:

1. **Başka bir sunucu/NAS** varsa: `rsync`/`scp` ile günlük kopya (cron).
2. **Nesne depolama** (S3/R2/Backblaze) hesabı varsa: `rclone` ile senkron.
3. Hiçbiri yoksa: **kullanıcıya sor**, kendi başına hesap açma. Geçici olarak
   en azından yedeklerin farklı bir dizinde/diskte tutulup tutulamayacağına bak.

Kurarsan bir kez elle çalıştır ve karşı tarafta dosyanın oluştuğunu **gör**.

---

## İş 3 — journald boyut sınırı

`/var/log/journal` 209 MB ve sınırsız büyüyor. %71 dolu diskte sessiz bir risk.

```bash
sudo mkdir -p /etc/systemd/journald.conf.d
# /etc/systemd/journald.conf.d/size.conf içeriği:
#   [Journal]
#   SystemMaxUse=200M
sudo systemctl restart systemd-journald    # servis günlüğü, uygulamayı ETKİLEMEZ
journalctl --disk-usage
```

`systemd-journald` yeniden başlatmak konteynerlere dokunmaz. Yine de emin değilsen
sadece dosyayı yaz, restart'ı bildir, kullanıcı karar versin.

Bu adım `sudo` istiyor ve parola gerekiyor — erişemiyorsan atla ve raporla.

---

## İş 4 — Bellek limitinin gerçekten uygulandığını doğrula

2026-09-05 akşamı `mem_limit: 1g` + heap tavanı 512 MB canlıya alındı. Uygulama
açıldı ve sağlıklı, **ama JVM'in limiti gördüğü ölçülmedi.** Fazla dar kalırsa
`-XX:+ExitOnOutOfMemoryError` yüzünden konteyner yük altında ölür.

Salt okuma, tek komut dizisi:

```bash
CID=$(docker ps -qf name=gserp-app)
docker inspect --format='mem_limit={{.HostConfig.Memory}}' "$CID"
docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.MemPerc}}' "$CID"
docker exec "$CID" sh -c 'java -XX:+PrintFlagsFinal -version 2>/dev/null | grep -E "MaxHeapSize|MaxRAM "'
```

**Beklenen:** `mem_limit=1073741824`, `MaxHeapSize` ~536 MB civarı, kullanım
limitin belirgin altında.

**Raporla:** üç çıktıyı da. Kullanım %85'in üstündeyse limit yükseltilmeli;
%40'ın altındaysa daha da sıkılabilir.

---

## Yapmayacakların

- Deploy tetikleme
- `gserp_db`'ye yazma (geri yükleme **yalnızca** geçici veritabanına)
- 6 ölü nginx sitesini silme — ayrı bir karar, dokunma
- İkinci konteyner / nginx `upstream` işine girme

## Rapor

1. Geri yükleme provası: tablo/satır sayıları, Flyway sürümü, geçici DB silindi mi
2. Dışarı kopyalama: ne kuruldu, kurulmadıysa neden
3. journald: sınır kondu mu
4. Sunucuda bunların dışında değişen bir şey **olmadığı**
