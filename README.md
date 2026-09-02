# GSCRM — Güzellik Salonu CRM

Spring Boot 3.4 + Java 21 üzerinde çalışan, randevu / kaynak / personel yönetimi sunan monolitik web uygulaması. Thymeleaf ile sunucu-render, STOMP/WebSocket ile canlı takvim güncellemesi.

> Bu branch (`production-ready`) projeyi in-memory MockDataStore'dan PostgreSQL + JPA + Flyway'e taşır, hibrit (form + JWT) kimlik doğrulama ekler ve Docker ile deploy edilebilir hâle getirir.

## Profiller

| Profil | Açıklama | Veritabanı |
|---|---|---|
| `dev` | Yerel geliştirme, demo seed verisi yüklenir | PostgreSQL (yerel) |
| `prod` | Üretim, seed yok, tüm config env'den | PostgreSQL (compose) |

## Yerel Geliştirme

Önkoşul: Java 21, Maven 3.9+, Docker (sadece DB için).

```bash
# 1) Sadece DB'yi ayağa kaldır
docker compose -f docker-compose.dev.yml up -d db

# 2) App'i dev profili ile çalıştır
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# (Windows)
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Tarayıcı: <http://localhost:8989> · varsayılan dev kullanıcısı: `admin / admin`.

**Tam dokümantasyon:** [docs/PROJECT_REFERENCE.md](docs/PROJECT_REFERENCE.md)

## Production Deploy (Docker Compose)

```bash
cp .env.example .env
# .env'i gerçek değerlerle doldur (DB_PASSWORD, JWT_SECRET, APP_CORS_ALLOWED_ORIGINS)

docker compose up -d --build
```

Önüne Nginx + Let's Encrypt yerleştirmek için `docs/deploy-vps.md` (Aşama 5'te eklenecek).

## Yapı

```
src/main/java/com/gscrm/
  config/      — Web, WebSocket, Security
  controller/  — REST + Page controller'ları
  dto/         — request/response DTO'ları
  exception/   — Domain exception'ları + GlobalExceptionHandler
  model/       — JPA entity'leri
  repository/  — Spring Data JPA repository'leri
  security/    — JWT, UserDetailsService, AuthController
  service/     — Business logic

src/main/resources/
  application.yml         — ortak ayarlar
  application-dev.yml     — dev
  application-prod.yml    — prod
  db/migration/           — Flyway SQL migration'ları
  static/                 — JS/CSS
  templates/              — Thymeleaf HTML'leri
```

## Environment Variables

Bkz. [.env.example](.env.example).

## Lisans

Ticari kullanım için lisanslanabilir salon ERP yazılımı. Detaylar için proje sahibi ile iletişime geçin.
