#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/var/backups/gscrm
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$BACKUP_DIR/gscrm-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

docker exec -t gscrm-db pg_dump -U gscrm -d gscrm --no-owner --clean --if-exists \
  | gzip -9 > "$OUT"

find "$BACKUP_DIR" -name 'gscrm-*.sql.gz' -mtime +14 -delete

echo "Backup: $OUT ($(du -h "$OUT" | cut -f1))"
