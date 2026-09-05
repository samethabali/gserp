# SSH görevi #2 — yedekleme, disk ve deploy engelinin kaldırılması

SSH erişimi olan makinedeki asistan için. Önceki görev salt okumaydı; **bu görevde
sunucuda değişiklik yapılıyor.** Üç iş var, ikisi prod'a hiç dokunmuyor.

Önceki toplama raporu: [`docs/ssh-yapılanlar.md`](ssh-yapılanlar.md)

---

## Durum

- `https://gscrm.avesitesi.xyz` canlıda. Davet kodları gönderildi ama **kontrol
  edildi: henüz hiçbiri kullanılmamış.** Yine de sistem ayakta kalmalı.
- Deploy'lar donduruldu; iş `staging` dalında yapılıyor.
- Uygulama tek konteynerde, oturumlar bellekte. Ölçülen kesinti süresi
  konteyner yenilemede **~100 saniye** (5 deploy ortalaması: 98-102 sn).

## Kesin kurallar

| Yasak | Neden |
|---|---|
| `docker compose up/down/build`, `docker restart` | Kesinti yaratır — bu görevde gerekmiyor |
| `docker volume prune` | Ölü bir sitenin veritabanı öksüz volume'ler arasında (`yahyakaptan_pgdata`, 113 MB) |
| `.env` içeriğini okumak/paylaşmak | Sırlar |
| `docker compose config` | `.env`'deki parolaları düz metin basar |
| `production-ready`'ye push | Otomatik deploy tetikler |

Erişim notu (önceki görevden): SSH `emre` ile açılıyor, `gserp`'in ev dizini 750.
`gserp` olarak iş yapmak için:
```bash
ssh -i /home/emre/gserp_deploy gserp@localhost '<komut>'
```

---

## İş 1 — 🔴 Veritabanı yedeklemesi (en öncelikli)

`gserp_db` (13 MB) şu an **yedeksiz** ve pilot müşteriler canlıya alınmak üzere.
Prod'a dokunmaz, kesinti yaratmaz.

Veritabanı paylaşımlı `postgres:16` konteynerinde; bağlantı bilgileri `.env`'de
(`DB_USER`, `DB_NAME`, `DB_PASSWORD`). **Parolayı ekrana basma** — script içinden
`.env` okunsun.

Kurulacak: günlük `pg_dump`, sıkıştırılmış, 14 gün rotasyon.

```bash
# /home/gserp/backup-gserp-db.sh  (gserp kullanıcısına ait, chmod 700)
#!/bin/sh
set -eu
ENV_FILE=/home/gserp/gserp/.env
DEST=/home/gserp/backups
mkdir -p "$DEST"
DB_USER=$(grep -E '^DB_USER=' "$ENV_FILE" | cut -d= -f2-)
DB_NAME=$(grep -E '^DB_NAME=' "$ENV_FILE" | cut -d= -f2-)
STAMP=$(date +%Y%m%d-%H%M)
docker exec postgres pg_dump -U "${DB_USER:-gserp_user}" "${DB_NAME:-gserp_db}" \
  | gzip > "$DEST/gserp_db-$STAMP.sql.gz"
find "$DEST" -name 'gserp_db-*.sql.gz' -mtime +14 -delete
```

Sonra `gserp` crontab'ına günlük iş (gece 04:00 gibi, deploy penceresiyle
çakışmasın).

**Doğrulama — bunu mutlaka yap:** Script'i bir kez elle çalıştır, dosyanın
oluştuğunu ve **boyutunun sıfır olmadığını** gör, sonra
`gunzip -t <dosya>` ile arşivin bozuk olmadığını doğrula. Cron'a bakıp
"kurdum" deme; çalıştığını görmeden bitmiş sayılmaz.

---

## İş 2 — Disk: build cache temizliği

```bash
docker builder prune -f
```

~3.9 GB geri gelir. Çalışan hiçbir şeye dokunmaz, imajları silmez. Öncesi ve
sonrası için `df -h /` çıktısını kaydet.

⚠️ `docker system prune` **kullanma** — o, öksüz volume'lere de uzanabilir.
Yalnızca `builder prune`.

---

## İş 3 — Deploy engelini kaldır (bir sonraki deploy bunsuz başarısız olur)

`docker-compose.prod.yml` artık **repoda izleniyor** (staging dalında). Sunucudaki
kopya ise git'in görmediği **takipsiz** bir dosya. Deploy script'i
`git merge --ff-only` yapıyor; git, takipsiz bir dosyanın üzerine yazılmasına
izin vermeyip **deploy'u durdurur**:

```
error: The following untracked working tree files would be overwritten by merge:
	docker-compose.prod.yml
```

Yapılacak — sunucudaki kopyayı yedekleyip kenara al:

```bash
ssh -i /home/emre/gserp_deploy gserp@localhost '
  cd /home/gserp/gserp
  git status --porcelain docker-compose.prod.yml     # "??" ise takipsiz, beklenen bu
  cp -a docker-compose.prod.yml /home/gserp/docker-compose.prod.yml.sunucu-yedek
  mv docker-compose.prod.yml docker-compose.prod.yml.git-oncesi
  ls -la docker-compose.prod.yml* 2>/dev/null
'
```

**Önemli:** Taşımadan önce sunucudaki dosyanın repodaki kopyayla **aynı içerikte
olduğunu** doğrula. Repodaki sürüm (staging, commit `445cb5a`) sunucudan alınmıştı;
o tarihten sonra sunucuda elle bir değişiklik yapılmışsa kaybolur.

```bash
# repo kopyası bu makinede varsa:
ssh -i /home/emre/gserp_deploy gserp@localhost 'cat /home/gserp/gserp/docker-compose.prod.yml' \
  | diff - <(git show 445cb5a:docker-compose.prod.yml) && echo "AYNI" || echo "FARKLI — RAPORLA, taşıma"
```

Farklıysa **taşıma**, farkı raporla.

---

## Yapmayacakların

- Deploy **tetikleme**. Yeni compose ayarları (`mem_limit`, log rotasyonu) staging
  dalında hazır ama canlıya alınması ayrı bir karar — gece penceresinde,
  kullanıcıyla birlikte yapılacak.
- `production-ready` dalına dokunma.
- İkinci konteyner / nginx `upstream` işine girme.

## Rapor

1. Yedek script'i kuruldu mu, **elle çalıştırılıp doğrulandı mı**, dosya boyutu ne
2. Cron satırı ne oldu, hangi kullanıcının crontab'ında
3. `builder prune` öncesi/sonrası boş disk
4. Compose dosyaları aynı mıydı; takipsiz kopya taşındı mı
5. Sunucuda bunların dışında değiştirilen bir şey **olmadığı**
