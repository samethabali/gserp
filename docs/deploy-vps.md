# GSCRM — VPS Deployment

Bu doküman GSCRM'i tek bir Linux VPS'e (Ubuntu 22.04 / 24.04 LTS varsayılır) Docker
Compose üzerinden, nginx reverse proxy ve Let's Encrypt TLS ile yayına almanın
adım adım yönergesidir.

> **Hızlı yol:** Aşağıdaki adımların büyük kısmını `scripts/vps-setup.sh` script'i
> idempotent (zaten var olanı atlar) şekilde yapar. Önce sistem kurulumu (Docker,
> nginx, UFW, certbot) elle tamamlanmalı; sonrasında script GSCRM'e özgü kısmı
> (clone, .env, compose up, nginx site, backup cron) otomatikleştirir. Yine de
> bu doküman referans olarak baştan adım adım açıklar.

Hedef mimari:

```
İnternet ── 443/80 ──> nginx (host) ── 127.0.0.1:8989 ──> gscrm-app (container)
                                                              │
                                                              ▼
                                                         gscrm-db (container)
                                                         (sadece compose ağı, host'a kapalı)
```

---

## 0. Önkoşullar

- Ubuntu 22.04 / 24.04 LTS, sudo yetkili kullanıcı (`deploy` varsayılır)
- DNS A kaydı `salon.example.com` → VPS public IP'sine işaret etmiş olmalı
- En az 2 vCPU / 2 GB RAM / 20 GB disk (küçük salon yükü için)

```bash
sudo adduser deploy
sudo usermod -aG sudo deploy
sudo rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

Sonraki adımları `deploy` kullanıcısı ile çalıştır.

---

## 1. Sistem güncellemesi ve temel araçlar

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl git ufw fail2ban
```

---

## 2. Docker + Compose kurulumu

Resmî Docker repo'su (Ubuntu'nun deposundaki sürüm eski olabilir):

```bash
# Anahtar + repo
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# deploy kullanıcısını docker grubuna ekle (sudo'suz docker için)
sudo usermod -aG docker deploy
# Yeni grup üyeliği bu oturumda etkin değil — logout/login veya:
newgrp docker

docker --version
docker compose version
```

---

## 3. Firewall (UFW)

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH    # 22
sudo ufw allow 80/tcp     # HTTP (certbot + redirect)
sudo ufw allow 443/tcp    # HTTPS
sudo ufw enable
sudo ufw status verbose
```

**Önemli:** GSCRM app container'ı `127.0.0.1:8989`'a bind ediyor ve DB container'ı
host'a hiç port açmıyor. 80/443 dışında hiçbir uygulama portunu UFW'da açma.

---

## 4. Kaynak kodu çekme + .env

```bash
sudo mkdir -p /opt/gscrm
sudo chown deploy:deploy /opt/gscrm
cd /opt/gscrm
git clone https://github.com/<your-org>/gscrm.git .
git checkout production-ready   # veya tagged release

cp .env.example .env
```

`.env`'yi düzenle (`nano .env` veya `vim .env`):

```bash
# DB
DB_PASSWORD=<openssl rand -base64 32 ile üret>

# JWT secret (BASE64, en az 256-bit)
JWT_SECRET=<openssl rand -base64 48 ile üret>

# CORS — uygulamanın gerçek domain'i
APP_CORS_ALLOWED_ORIGINS=https://salon.example.com

# İlk admin (kurulumdan sonra UI'dan parolayı değiştir)
GSCRM_INITIAL_ADMIN_USERNAME=admin
GSCRM_INITIAL_ADMIN_PASSWORD=<güçlü-tek-kullanımlık-parola>
```

`.env` dosyasının izinleri:

```bash
chmod 600 .env
```

---

## 5. Stack'i başlat

```bash
cd /opt/gscrm
docker compose up -d --build
docker compose ps   # her ikisi de "Up (healthy)" olmalı
docker compose logs -f app | head -40
```

Sağlık kontrolü:

```bash
curl -fsS http://localhost:8989/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

İlk açılışta `gscrm-app` loglarında şunu görmelisin:

```
INFO  com.gscrm.config.InitialAdminSeeder - İlk admin kullanıcısı oluşturuldu: 'admin'.
```

---

## 6. nginx kurulumu ve reverse proxy

```bash
sudo apt install -y nginx
sudo systemctl enable --now nginx
```

`/etc/nginx/sites-available/gscrm` dosyasını oluştur:

```nginx
# /etc/nginx/sites-available/gscrm
upstream gscrm_app {
    server 127.0.0.1:8989;
    keepalive 32;
}

# HTTP -> HTTPS redirect (certbot kuruluncaya kadar bu blok yeterli;
# certbot çalıştığında otomatik düzenlenir)
server {
    listen 80;
    listen [::]:80;
    server_name salon.example.com;

    # Let's Encrypt webroot challenge için
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name salon.example.com;

    # certbot bu üç satırı kendisi dolduracak:
    # ssl_certificate     /etc/letsencrypt/live/salon.example.com/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/salon.example.com/privkey.pem;
    # include /etc/letsencrypt/options-ssl-nginx.conf;

    # Güvenlik başlıkları
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Body boyutu (Spring Boot multipart default 1MB — istersen büyüt)
    client_max_body_size 10M;

    # Public booking rate limit (nginx ikinci katman — uygulama içi BookingRateLimitFilter ile birlikte)
    limit_req_zone $binary_remote_addr zone=booking_api:10m rate=10r/m;
    limit_req_zone $binary_remote_addr zone=booking_get:10m rate=60r/m;

    location /api/booking/request {
        limit_req zone=booking_api burst=5 nodelay;
        proxy_pass http://gscrm_app;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    location /api/booking/ {
        limit_req zone=booking_get burst=20 nodelay;
        proxy_pass http://gscrm_app;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    # ───────── REST + sayfa trafiği ─────────
    location / {
        proxy_pass http://gscrm_app;

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;

        proxy_http_version 1.1;
        proxy_set_header Connection "";

        proxy_connect_timeout  10s;
        proxy_send_timeout     60s;
        proxy_read_timeout     60s;
    }

    # ───────── WebSocket / SockJS endpoint'i ─────────
    # Spring STOMP "/ws-calendar/**" altında çalışıyor; SockJS fallback
    # transport'larıyla birlikte uzun-poll, xhr-streaming ve websocket
    # frame'lerini bu blok taşır.
    location /ws-calendar/ {
        proxy_pass http://gscrm_app;

        proxy_http_version 1.1;
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        "upgrade";
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket bağlantısı uzun ömürlü — 1 saatlik read timeout
        proxy_read_timeout  3600s;
        proxy_send_timeout  3600s;
        proxy_buffering     off;
    }

    # actuator dışarıya açılmasın (DigitalOcean health probe içinden ya da
    # özel bir lokasyondan eriş)
    location /actuator/ {
        deny all;
    }
}
```

Etkinleştir + test et:

```bash
sudo ln -s /etc/nginx/sites-available/gscrm /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo mkdir -p /var/www/certbot
sudo nginx -t
sudo systemctl reload nginx
```

---

## 7. Let's Encrypt sertifikası (certbot)

```bash
sudo apt install -y certbot python3-certbot-nginx

# nginx pluginiyle: certbot, yukarıdaki conf'a ssl_certificate satırlarını
# otomatik ekler ve HTTP -> HTTPS redirect'i set eder.
sudo certbot --nginx -d salon.example.com \
  --non-interactive --agree-tos --email ops@example.com --redirect

# Yenileme cron'u zaten /etc/cron.d/certbot içinde. Manuel test:
sudo certbot renew --dry-run
```

Tarayıcıdan `https://salon.example.com` → login ekranı gelmeli.

---

## 8. fail2ban — SSH brute-force koruması

`/etc/fail2ban/jail.local`:

```ini
[DEFAULT]
bantime  = 1h
findtime = 10m
maxretry = 5

[sshd]
enabled = true
```

```bash
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd
```

---

## 9. PostgreSQL yedek stratejisi

Container içindeki Postgres'i hosttan `pg_dump`'lamak en pratik yol.

Yedek script'i (`/opt/gscrm/scripts/backup-db.sh`):

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/var/backups/gscrm
RETENTION_DAYS=14
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$BACKUP_DIR/gscrm-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

# Compose ağındaki gscrm-db container'ına gir, pg_dump çıktısını gzip'le
docker exec -t gscrm-db pg_dump -U gscrm -d gscrm --no-owner --clean --if-exists \
  | gzip -9 > "$OUT"

# Eski yedekleri temizle
find "$BACKUP_DIR" -name 'gscrm-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete

echo "Backup: $OUT ($(du -h "$OUT" | cut -f1))"
```

```bash
sudo mkdir -p /var/backups/gscrm /opt/gscrm/scripts
sudo chown deploy:deploy /var/backups/gscrm /opt/gscrm/scripts
chmod +x /opt/gscrm/scripts/backup-db.sh
```

Cron (deploy kullanıcısının crontab'ına — `crontab -e`):

```cron
# Her gece 03:30 UTC'de yedek
30 3 * * * /opt/gscrm/scripts/backup-db.sh >> /var/log/gscrm-backup.log 2>&1
```

### Geri yükleme (felaket kurtarma)

```bash
# Stack'i durdur, volume'ı sıfırla
cd /opt/gscrm
docker compose down
docker volume rm gscrm_gscrm-db-data

# DB'yi tek başına ayağa kaldır (app henüz çalışmasın — Flyway DB temizken bozulur)
docker compose up -d db
until [ "$(docker inspect --format='{{.State.Health.Status}}' gscrm-db)" = "healthy" ]; do
  sleep 2
done

# Yedeği geri yükle
gunzip -c /var/backups/gscrm/gscrm-YYYYMMDDTHHMMSSZ.sql.gz | \
  docker exec -i gscrm-db psql -U gscrm -d gscrm

# App'i başlat
docker compose up -d app
```

> **Not:** Yedek dosyaları VPS dışında ayrı bir konuma da kopyalanmalı (rclone ile
> S3/B2/Drive, ya da `rsync` ile ayrı bir sunucuya). Aynı diskteki yedek, disk
> arızasından kurtarmaz.

---

## 10. Yükseltme / yeniden deploy

Kod güncellemesi geldiğinde:

```bash
cd /opt/gscrm
git fetch --all
git checkout production-ready    # veya yeni tag
git pull

# Yedek al (önce)
./scripts/backup-db.sh

# Image'i tekrar build et, kademeli rollover
docker compose build app
docker compose up -d app

# Sağlık kontrolü
sleep 15
curl -fsS https://salon.example.com/actuator/health
```

Flyway yeni migration'ları açılışta otomatik uygular. Sorun çıkarsa loglar:

```bash
docker compose logs --tail 200 app
```

---

## 11. Operasyonel kontrol listesi

İlk deploy sonrası mutlaka:

- [ ] `https://salon.example.com` HSTS+TLS A+ (SSL Labs ile doğrula)
- [ ] Tarayıcıdan login: `admin / <env'deki parola>`
- [ ] UI'dan admin parolasını değiştir (env'deki tek-kullanımlık parolayı bırakma)
- [ ] `.env` dosyasının izni `600`, kullanıcısı `deploy` (`ls -l .env`)
- [ ] `ufw status` — sadece 22/80/443 açık
- [ ] `docker compose ps` — db ve app "healthy"
- [ ] Yedek cron'unu manuel tetikle (`./scripts/backup-db.sh`) ve `/var/backups/gscrm/`
      içinde dosya geldiğini doğrula
- [ ] `journalctl -u fail2ban` aktif
- [ ] WebSocket: iki tarayıcıda giriş yap, birinde randevu sürükle → diğerinde
      canlı güncellensin (websocket frame'lerinin nginx üzerinden geçtiğini gösterir)

---

## 12. Sorun giderme

| Belirti | Olası neden | Çözüm |
|---|---|---|
| `502 Bad Gateway` | app container down veya 8989 dinlemiyor | `docker compose ps`, `docker compose logs app` |
| Login 401 ama parola doğru | `.env` JWT_SECRET değişti, eski token kullanıyor | Tarayıcı çerezini sil, yeniden login |
| WS bağlanmıyor (`/ws-calendar` 502) | nginx WS bloğu eksik / proxy_read_timeout düşük | Bu doc'taki location bloğunu kontrol et |
| Flyway "validation failed" | Migration dosyası deploy sonrası değişti | **Asla** üretimde uygulanmış migration'ı değiştirme; yeni V3, V4… ekle |
| CORS hatası | `APP_CORS_ALLOWED_ORIGINS` domain'le eşleşmiyor | `.env`'i düzelt, `docker compose up -d` ile restart |
| 5432 hostta dinleniyor | Yanlışlıkla docker-compose.dev.yml deploy edildi | Sadece `docker-compose.yml` (prod) kullan |

---

## 13. Multi-Tenant SaaS (tek domain, yol tabanlı kiracı)

Tek `gscrm-app` instance, shared PostgreSQL. Tenant `{slug}.gscrm.avesitesi.xyz` ile çözülür.

Detaylı nginx config: [`docs/saas/nginx-single-domain.md`](saas/nginx-single-domain.md)

```bash
# DNS
*.gscrm.avesitesi.xyz  →  VPS IP

# Deploy (image_transfer önerilir — bkz. MCP deploy_kurallari.yaml)
docker compose up -d --build

# Smoke test
curl -H "X-Salon-Slug: default" https://gscrm.avesitesi.xyz/api/booking/services
curl https://gscrm.avesitesi.xyz/actuator/health
```

DR: [`docs/saas/dr-playbook.md`](saas/dr-playbook.md)
