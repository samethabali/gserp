# GSCRM SaaS — Hizmet Seviyesi Anlaşması (SLA)

## Uptime

| Plan | Hedef uptime | Aylık max kesinti |
|------|--------------|-------------------|
| SOLO / FRANCHISE | %99.5 | ~3.6 saat |
| ENTERPRISE | %99.9 | ~43 dakika |

## Destek Yanıt Süreleri

| Öncelik | Tanım | SOLO | FRANCHISE | ENTERPRISE |
|---------|-------|------|-----------|------------|
| P1 | Sistem down | 4 saat | 2 saat | 1 saat |
| P2 | Kritik özellik | 1 iş günü | 8 saat | 4 saat |
| P3 | Genel soru | 3 iş günü | 2 iş günü | 1 iş günü |

## Yedekleme

- Günlük PostgreSQL yedek, 14 gün retention
- RPO: 24 saat | RTO: 4 saat (ENTERPRISE: 1 saat)

## Planlı Bakım

- Hafta içi 02:00–04:00 UTC, 48 saat önceden bildirim
- Durum sayfası: `status.gscrm.avesitesi.xyz` (hedef)

## Hariç Tutulanlar

- Müşteri internet/VDS dışı kesintiler
- Meta WhatsApp API kesintileri
- Force majeure
