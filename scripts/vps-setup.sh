#!/usr/bin/env bash
# GSERP — VPS kurulum otomasyonu (idempotent).
#
# Kapsam: GSERP'e özgü kısım. apt/docker/ufw/certbot gibi sistem-seviyesi
# kurulum BU SCRIPTTE YOKTUR; onlar deploy-vps.md'de el ile yapılır.
#
# Bu script:
#   1. Gerekli sistem araçlarının (docker, compose, nginx, certbot) varlığını
#      DOĞRULAR (eksikse durur, ne yapacağını söyler).
#   2. /opt/gserp altına repo'yu clone'lar veya günceller.
#   3. .env yoksa interaktif olarak oluşturur (secret'ları openssl rand ile üretir).
#   4. docker compose'u ayağa kaldırır, health bekler.
#   5. /etc/nginx/sites-available/gserp dosyasını yazar (mevcutsa diff gösterip
#      onay ister). Mevcut diğer site'lara dokunmaz.
#   6. Yedek script'i + cron girdisini ekler (zaten varsa atlar).
#
# Çalıştırma:
#   sudo bash vps-setup.sh
# Veya sudo'suz, gerekli yerlerde sudo isteyecek şekilde:
#   bash vps-setup.sh
#
# Yeniden çalıştırmak güvenlidir: her adım "zaten var mı?" kontrol eder.

set -euo pipefail

# ───────────────────── Ayarlar ─────────────────────
REPO_URL="${REPO_URL:-}"                              # Boşsa kullanıcıya sorulur
INSTALL_DIR="${INSTALL_DIR:-/opt/gserp}"
BRANCH="${BRANCH:-production-ready}"
NGINX_SITE_NAME="${NGINX_SITE_NAME:-gserp}"
DOMAIN="${DOMAIN:-}"                                  # Boşsa kullanıcıya sorulur
BACKUP_DIR="${BACKUP_DIR:-/var/backups/gserp}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

# ───────────────────── Yardımcılar ─────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[*]${NC} $*"; }
ok()   { echo -e "${GREEN}[+]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[x]${NC} $*" >&2; }
die()  { err "$*"; exit 1; }

ask() {
    # ask "Soru" "default" -> stdout cevap
    local prompt="$1" default="${2:-}" reply
    if [[ -n "$default" ]]; then
        read -rp "$prompt [$default]: " reply
        echo "${reply:-$default}"
    else
        read -rp "$prompt: " reply
        echo "$reply"
    fi
}

confirm() {
    # confirm "Soru" -> 0 yes, 1 no
    local reply
    read -rp "$1 [y/N]: " reply
    [[ "$reply" =~ ^[Yy]$ ]]
}

need_root_for() {
    # need_root_for "/etc/nginx" -> sudo gerekiyorsa SUDO=sudo set eder
    if [[ -w "$1" ]] || [[ "$EUID" -eq 0 ]]; then
        SUDO=""
    else
        SUDO="sudo"
    fi
}

# ───────────────────── 0. Sistem önkoşulları ─────────────────────
log "Sistem önkoşulları kontrol ediliyor..."

MISSING=()
command -v docker >/dev/null      || MISSING+=("docker")
docker compose version >/dev/null 2>&1 || MISSING+=("docker compose (plugin)")
command -v git >/dev/null         || MISSING+=("git")
command -v openssl >/dev/null     || MISSING+=("openssl")
command -v nginx >/dev/null       || MISSING+=("nginx")
command -v curl >/dev/null        || MISSING+=("curl")

if [[ ${#MISSING[@]} -gt 0 ]]; then
    err "Eksik sistem araçları: ${MISSING[*]}"
    err "Önce bunları kur, sonra bu script'i çalıştır. deploy-vps.md bölüm 2 + 6'ya bak."
    exit 2
fi
ok "Tüm sistem araçları hazır"

# certbot varsa bilgi ver — sertifika kurulumunu script yapmaz
if command -v certbot >/dev/null; then
    ok "certbot kurulu (sertifika kurulumu kullanıcıya bırakıldı)"
else
    warn "certbot kurulu değil — HTTPS için 'apt install certbot python3-certbot-nginx' sonrası"
    warn "  sudo certbot --nginx -d <domain>"
fi

# Docker grubu üyeliği
if [[ "$EUID" -ne 0 ]]; then
    if ! groups | grep -q '\bdocker\b'; then
        warn "Kullanıcı docker grubunda değil. 'sudo usermod -aG docker $USER' + relogin önerilir."
        warn "  Script devam edecek, docker komutları sudo ile çağrılacak."
        DOCKER="sudo docker"
    else
        DOCKER="docker"
    fi
else
    DOCKER="docker"
fi

# ───────────────────── 1. Repo clone / update ─────────────────────
log "Repo: $INSTALL_DIR ($BRANCH)"

need_root_for "$(dirname "$INSTALL_DIR")"
$SUDO mkdir -p "$INSTALL_DIR"
$SUDO chown "$USER:$USER" "$INSTALL_DIR"

if [[ -d "$INSTALL_DIR/.git" ]]; then
    ok "Repo zaten clone'lu — fetch + checkout"
    git -C "$INSTALL_DIR" fetch --all --tags
    git -C "$INSTALL_DIR" checkout "$BRANCH"
    git -C "$INSTALL_DIR" pull --ff-only
else
    if [[ -z "$REPO_URL" ]]; then
        REPO_URL=$(ask "Git repo URL'si (HTTPS veya SSH)")
        [[ -z "$REPO_URL" ]] && die "Repo URL gerekli"
    fi
    log "Clone: $REPO_URL"
    git clone --branch "$BRANCH" "$REPO_URL" "$INSTALL_DIR"
fi
ok "Repo hazır: $(git -C "$INSTALL_DIR" rev-parse --short HEAD)"

cd "$INSTALL_DIR"

# ───────────────────── 2. .env ─────────────────────
if [[ -f "$INSTALL_DIR/.env" ]]; then
    ok ".env zaten var — atlanıyor (gerekirse elle düzenle)"
else
    log ".env oluşturuluyor..."
    [[ -z "$DOMAIN" ]] && DOMAIN=$(ask "Uygulamanın domain'i (örn. salon.example.com)")
    [[ -z "$DOMAIN" ]] && die "Domain gerekli"

    ADMIN_USER=$(ask "İlk admin kullanıcı adı" "admin")
    ADMIN_PASS=$(ask "İlk admin parolası (boş bırakılırsa random üretilir)")
    if [[ -z "$ADMIN_PASS" ]]; then
        ADMIN_PASS=$(openssl rand -base64 18)
        warn "Random admin parolası üretildi (.env'de saklanacak): $ADMIN_PASS"
    fi

    DB_PW=$(openssl rand -base64 32)
    JWT_SEC=$(openssl rand -base64 48)

    cat > "$INSTALL_DIR/.env" <<ENV
# GSERP production .env — $(date -u +%Y-%m-%dT%H:%M:%SZ) tarihinde üretildi
DB_PASSWORD=$DB_PW

SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/gserp
SPRING_DATASOURCE_USERNAME=gserp
SPRING_DATASOURCE_PASSWORD=$DB_PW

JWT_SECRET=$JWT_SEC

APP_CORS_ALLOWED_ORIGINS=https://$DOMAIN

SPRING_PROFILES_ACTIVE=prod

GSERP_INITIAL_ADMIN_USERNAME=$ADMIN_USER
GSERP_INITIAL_ADMIN_PASSWORD=$ADMIN_PASS
ENV
    chmod 600 "$INSTALL_DIR/.env"
    ok ".env yazıldı (chmod 600)"
fi

# ───────────────────── 3. Docker compose up ─────────────────────
log "docker compose build + up"
$DOCKER compose -f "$INSTALL_DIR/docker-compose.yml" build app
$DOCKER compose -f "$INSTALL_DIR/docker-compose.yml" up -d

log "App'in healthy duruma gelmesi bekleniyor (max 120s)..."
DEADLINE=$(( $(date +%s) + 120 ))
while [[ $(date +%s) -lt $DEADLINE ]]; do
    STATUS=$($DOCKER inspect --format='{{.State.Health.Status}}' gserp-app 2>/dev/null || echo "missing")
    if [[ "$STATUS" == "healthy" ]]; then
        ok "gserp-app healthy"
        break
    fi
    sleep 5
done
if [[ "$STATUS" != "healthy" ]]; then
    err "gserp-app healthy olmadı (durum: $STATUS). Logları kontrol et:"
    err "  docker compose logs --tail 100 app"
    exit 3
fi

if curl -fsS http://127.0.0.1:8989/actuator/health | grep -q '"status":"UP"'; then
    ok "/actuator/health UP"
else
    warn "/actuator/health beklendiği gibi yanıtlamadı — yine de devam"
fi

# ───────────────────── 4. nginx site ─────────────────────
NGX_AVAIL="/etc/nginx/sites-available/$NGINX_SITE_NAME"
NGX_ENABLED="/etc/nginx/sites-enabled/$NGINX_SITE_NAME"
need_root_for "/etc/nginx/sites-available"

if [[ -z "${DOMAIN}" ]]; then
    # .env'den oku
    DOMAIN=$(grep -E '^APP_CORS_ALLOWED_ORIGINS=' "$INSTALL_DIR/.env" | sed 's|.*://||; s|/.*||' | head -1)
fi
[[ -z "$DOMAIN" ]] && die "Domain belirlenemedi — DOMAIN env var olarak ver veya .env'i kontrol et"

NGX_CONFIG=$(cat <<NGINX
# GSERP — vps-setup.sh tarafından üretildi
upstream gserp_app {
    server 127.0.0.1:8989;
    keepalive 32;
}

server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN;

    # ssl_certificate satırlarını certbot --nginx çalıştığında dolduracak.
    # Eğer certbot çalıştırılmadıysa nginx -t bu blokta hata verir; o durumda
    # bu server bloğunu yorum satırı yap, certbot'u çalıştır, sonra yorumları aç.

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    client_max_body_size 10M;

    location / {
        proxy_pass http://gserp_app;
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host  \$host;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_connect_timeout  10s;
        proxy_send_timeout     60s;
        proxy_read_timeout     60s;
    }

    location /ws-calendar/ {
        proxy_pass http://gserp_app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade           \$http_upgrade;
        proxy_set_header Connection        "upgrade";
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout  3600s;
        proxy_send_timeout  3600s;
        proxy_buffering     off;
    }

    location /actuator/ {
        deny all;
    }
}
NGINX
)

if [[ -f "$NGX_AVAIL" ]]; then
    if echo "$NGX_CONFIG" | diff -q - "$NGX_AVAIL" >/dev/null; then
        ok "nginx site config zaten güncel"
    else
        warn "nginx site config'i bu script'in ürettiğinden FARKLI:"
        echo "---"
        echo "$NGX_CONFIG" | diff -u "$NGX_AVAIL" - || true
        echo "---"
        if confirm "Yeni içeriği yazayım mı? (eskisi .bak olarak saklanır)"; then
            $SUDO cp "$NGX_AVAIL" "$NGX_AVAIL.bak.$(date +%s)"
            echo "$NGX_CONFIG" | $SUDO tee "$NGX_AVAIL" > /dev/null
            ok "yazıldı (yedek: $NGX_AVAIL.bak.*)"
        else
            warn "Mevcut config korundu"
        fi
    fi
else
    echo "$NGX_CONFIG" | $SUDO tee "$NGX_AVAIL" > /dev/null
    ok "$NGX_AVAIL yazıldı"
fi

if [[ ! -L "$NGX_ENABLED" ]]; then
    $SUDO ln -s "$NGX_AVAIL" "$NGX_ENABLED"
    ok "site enabled"
fi

# certbot webroot için dizin
$SUDO mkdir -p /var/www/certbot

# nginx test + reload (SSL satırları yoksa ilk seferde hata verebilir)
if $SUDO nginx -t 2>/dev/null; then
    $SUDO systemctl reload nginx
    ok "nginx reloaded"
else
    warn "nginx -t hatası verdi (muhtemelen TLS sertifikası henüz yok)"
    warn "Şu adımları izle:"
    warn "  1. 443 server bloğunu geçici olarak yorum satırı yap"
    warn "  2. sudo systemctl reload nginx"
    warn "  3. sudo certbot --nginx -d $DOMAIN"
    warn "  4. certbot 443 bloğunu otomatik düzenleyecek + ssl_certificate satırlarını ekleyecek"
fi

# ───────────────────── 5. Yedek script + cron ─────────────────────
BACKUP_SCRIPT="$INSTALL_DIR/scripts/backup-db.sh"

if [[ ! -f "$BACKUP_SCRIPT" ]]; then
    mkdir -p "$INSTALL_DIR/scripts"
    cat > "$BACKUP_SCRIPT" <<'BACKUP'
#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/var/backups/gserp}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$BACKUP_DIR/gserp-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"
docker exec -t gserp-db pg_dump -U gserp -d gserp --no-owner --clean --if-exists \
  | gzip -9 > "$OUT"
find "$BACKUP_DIR" -name 'gserp-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
echo "Backup: $OUT ($(du -h "$OUT" | cut -f1))"
BACKUP
    chmod +x "$BACKUP_SCRIPT"
    ok "Yedek script yazıldı: $BACKUP_SCRIPT"
else
    ok "Yedek script zaten var"
fi

need_root_for "$BACKUP_DIR"
$SUDO mkdir -p "$BACKUP_DIR"
$SUDO chown "$USER:$USER" "$BACKUP_DIR"

# Crontab girdisi
CRON_LINE="30 3 * * * BACKUP_RETENTION_DAYS=$BACKUP_RETENTION_DAYS BACKUP_DIR=$BACKUP_DIR $BACKUP_SCRIPT >> /var/log/gserp-backup.log 2>&1"
EXISTING_CRON=$(crontab -l 2>/dev/null || true)
if echo "$EXISTING_CRON" | grep -qF "$BACKUP_SCRIPT"; then
    ok "Cron girdisi zaten var"
else
    ( echo "$EXISTING_CRON"; echo "$CRON_LINE" ) | crontab -
    ok "Cron girdisi eklendi (her gün 03:30 UTC)"
fi

$SUDO touch /var/log/gserp-backup.log
$SUDO chown "$USER:$USER" /var/log/gserp-backup.log

# ───────────────────── Özet ─────────────────────
echo ""
ok "Kurulum tamamlandı"
echo ""
echo "  Repo:          $INSTALL_DIR ($(git -C "$INSTALL_DIR" rev-parse --short HEAD))"
echo "  Domain:        $DOMAIN"
echo "  App:           https://$DOMAIN  (certbot ayarlandıysa)"
echo "  Health:        curl http://127.0.0.1:8989/actuator/health"
echo "  Logs:          docker compose -f $INSTALL_DIR/docker-compose.yml logs -f app"
echo "  Yedek script:  $BACKUP_SCRIPT (cron: 03:30 UTC)"
echo ""
warn "İlk girişten sonra UI'dan admin parolasını DEĞİŞTİR — .env'deki tek-kullanımlık"
warn "parolayı koruma altında bırakma."
