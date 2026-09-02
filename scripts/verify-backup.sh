#!/usr/bin/env bash
# GSCRM — yedek dosyası bütünlük kontrolü (DR smoke)
set -euo pipefail

BACKUP_DIR="${1:-/var/backups/gscrm}"
LATEST=$(ls -1t "$BACKUP_DIR"/gscrm-*.sql.gz 2>/dev/null | head -1 || true)

if [ -z "$LATEST" ]; then
  echo "[x] Yedek bulunamadı: $BACKUP_DIR"
  exit 1
fi

echo "[*] Kontrol: $LATEST"
gzip -t "$LATEST"
SIZE=$(du -h "$LATEST" | cut -f1)
echo "[+] gzip OK — boyut: $SIZE"
echo "[+] Restore testi için: gunzip -c $LATEST | head -20"
