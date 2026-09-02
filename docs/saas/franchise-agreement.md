# GSCRM — Franchise / SaaS Hizmet Sözleşmesi Taslağı

## Konu

GSCRM yazılımının franchise zinciri veya bağımsız salon tarafından bulut (SaaS) olarak kullanımı.

## Org Tipleri

- **STANDALONE:** Tek şube, salon sahibi = ORG_OWNER
- **FRANCHISE:** Çok şube, merkez = ORG_OWNER, şube müdürü = BRANCH_MANAGER

## Platform Yükümlülükleri

- Multi-tenant izolasyon (`salon_id`)
- SLA (bkz. `sla.md`)
- Güncelleme ve güvenlik yamaları

## Müşteri Yükümlülükleri

- Doğru iletişim bilgisi ve KVKK aydınlatması
- Kullanıcı hesap güvenliği
- Abonelik ücretlerinin zamanında ödenmesi

## Fikri Mülkiyet

- GSCRM kodu ve markası platforma aittir
- Salon müşteri verisi salona aittir

## Veri Taşınabilirliği

- Sözleşme bitiminde JSON/CSV export (API: `/api/customers/{id}/export`)

## Uyuşmazlık

- Türkiye Cumhuriyeti kanunları, İstanbul mahkemeleri
