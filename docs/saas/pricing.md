# GSCRM SaaS — Fiyatlandırma Modeli

## Plan Tipleri

| Plan | Hedef | Aylık (TRY) | Salon | Kullanıcı |
|------|-------|-------------|-------|-----------|
| SOLO | Bağımsız salon | 990 | 1 | 5 |
| FRANCHISE_STARTER | 2–5 şube | 2.490 | 5 | 20 |
| FRANCHISE_PRO | 6+ şube | Özel | Sınırsız | Sınırsız |
| ENTERPRISE | Dedicated instance | Özel | — | — |

## Metrikler

- **Şube başına:** Temel abonelik birimi
- **Seat (kullanıcı):** ADMIN/RECEPTIONIST/SPECIALIST hesapları

## Trial

- 14 gün ücretsiz (SOLO ve FRANCHISE_STARTER)
- Kredi kartı zorunlu değil; ödeme ürün dışı kanaldan (havale/fatura) alınır
- Trial bitince read-only + upgrade banner

## Bağımsız vs Franchise

- **STANDALONE org:** Otomatik SOLO plan, tek salon
- **FRANCHISE org:** FRANCHISE_STARTER minimum, şube ekleme seat kotasına tabi
