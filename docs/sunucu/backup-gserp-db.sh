#!/bin/bash
# gserp_db gunluk yedegi — docs/ssh-gorev-2.md Is 1
# Kurulum: 2026-09-05. gserp crontab'indan her gun 04:00'te calisir.
set -euo pipefail

ENV_FILE=/home/gserp/gserp/.env
DEST=/home/gserp/backups
KEEP_DAYS=14

mkdir -p "$DEST"

# Degerler yalnizca script icinde kullanilir, hicbir yere basilmaz.
DB_USER=$(grep -E '^DB_USER=' "$ENV_FILE" | cut -d= -f2- || true)
DB_NAME=$(grep -E '^DB_NAME=' "$ENV_FILE" | cut -d= -f2- || true)
DB_USER=${DB_USER:-gserp_user}
DB_NAME=${DB_NAME:-gserp_db}

STAMP=$(date +%Y%m%d-%H%M)
FINAL="$DEST/gserp_db-$STAMP.sql.gz"
TMP="$FINAL.tmp"

# pipefail sayesinde pg_dump hatasi gzip tarafindan yutulmuyor: once .tmp'ye
# yazilir, bos/bozuk degilse adi konur. Rotasyon YALNIZCA basarili bir
# yedekten sonra calisir; boylece bozuk bir dump saglam yedekleri silemez.
if ! docker exec postgres pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$TMP"; then
    rm -f "$TMP"
    echo "gserp-backup HATA: pg_dump basarisiz" >&2
    exit 1
fi

if [ ! -s "$TMP" ] || ! gunzip -t "$TMP" 2>/dev/null; then
    rm -f "$TMP"
    echo "gserp-backup HATA: uretilen arsiv bos veya bozuk" >&2
    exit 1
fi

mv "$TMP" "$FINAL"
find "$DEST" -name 'gserp_db-*.sql.gz'     -mtime +${KEEP_DAYS} -delete
find "$DEST" -name 'gserp_db-*.sql.gz.tmp' -mtime +1            -delete
echo "gserp-backup OK: $FINAL ($(stat -c%s "$FINAL") bayt)"
