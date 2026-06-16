#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/var/backups/gserp
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$BACKUP_DIR/gserp-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

docker exec -t gserp-db pg_dump -U gserp -d gserp --no-owner --clean --if-exists \
  | gzip -9 > "$OUT"

find "$BACKUP_DIR" -name 'gserp-*.sql.gz' -mtime +14 -delete

echo "Backup: $OUT ($(du -h "$OUT" | cut -f1))"
