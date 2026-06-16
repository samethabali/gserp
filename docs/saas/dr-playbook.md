# GSERP — Disaster Recovery Playbook

## Hedefler

| Metrik | Hedef |
|--------|-------|
| RPO | 24 saat (günlük yedek) |
| RTO | 4 saat |

## Yedekleme

```bash
# Tenant-aware tam DB yedek
./scripts/backup-db.sh

# Off-site kopya (opsiyonel)
BACKUP_OFFSITE_PATH=user@backup-host:/backups/gserp ./scripts/backup-db.sh
```

Retention: 14 gün (script içi).

## Restore

1. Uygulamayı durdur: `docker compose stop gserp-app`
2. DB volume yedekten geri yükle (bkz. `docs/deploy-vps.md` §9)
3. Flyway migrate doğrula: `docker compose run --rm gserp-app flyway info`
4. Health check: `curl -sf http://127.0.0.1:8989/actuator/health`
5. Tenant smoke test: `curl -H "X-Salon-Slug: default" http://127.0.0.1:8989/api/booking/services`

## Multi-Tenant DR Notları

- Tek shared DB — partial tenant restore desteklenmiyor; tenant export için `/api/customers/{id}/export`
- Wildcard nginx config yedekte: `/etc/nginx/sites-available/gserp-multitenant`
- `deploy_kurallari.yaml` image_transfer ile hızlı redeploy alternatifi

## Tatbikat (yılda 1)

1. Staging'de son yedeği restore et
2. 2 salon slug ile booking testi
3. RTO süresini kaydet
