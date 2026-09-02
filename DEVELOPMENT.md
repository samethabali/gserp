# GSCRM — Projeyi Ayağa Kaldırma Rehberi

## Gereksinimler

| Araç | Versiyon | Kontrol Komutu |
|------|----------|----------------|
| **Java** | 21+ | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Docker** | 27+ | `docker --version` |

---

## Hızlı Başlatma (Tek Komut)

PowerShell'de proje kök dizininde:

```powershell
.\start-dev.ps1
```

Bu script otomatik olarak:
1. PostgreSQL container'ını başlatır (port `5800`)
2. DB'nin hazır olmasını bekler
3. `SPRING_DATASOURCE_URL` env variable'ını set eder
4. `mvn spring-boot:run` ile uygulamayı başlatır

---

## Manuel Başlatma (Adım Adım)

### 1. PostgreSQL Veritabanını Başlat

```powershell
docker compose -f docker-compose.dev.yml up -d
```

Bu komut `gscrm-db-dev` adında bir PostgreSQL 16 container'ı başlatır:
- **Host port:** `5800`
- **DB adı:** `gscrm_dev`
- **Kullanıcı:** `gscrm`
- **Şifre:** `gscrm`

Kontrol:
```powershell
docker exec gscrm-db-dev pg_isready -U gscrm -d gscrm_dev
```

### 2. Spring Boot Uygulamasını Başlat

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5800/gscrm_dev"
mvn spring-boot:run
```

> **Önemli:** `SPRING_DATASOURCE_URL` set edilmezse uygulama varsayılan `localhost:5432` portuna bağlanmaya çalışır ve başarısız olur.

### 3. Uygulamaya Eriş

| Sayfa | URL |
|-------|-----|
| **Giriş** | http://localhost:8989 |
| **Randevu Sistemi** | http://localhost:8989/booking |
| **Demo rehberi** | [docs/DEMO.md](docs/DEMO.md) |

**Giriş bilgileri (dev mock veri — V7):**

| Kullanıcı | Şifre | Rol |
|-----------|-------|-----|
| `admin` | `admin` | Yönetici (BRANCH_MANAGER) |
| `merve` | `admin` | Resepsiyon |
| `ayse` | `admin` | Uzman |
| `fatma` | `admin` | Uzman |

Arkadaşlarla demo akışı için: [docs/DEMO.md](docs/DEMO.md)

---

## Veritabanını Sıfırlama

Mevcut verileri silip sıfırdan başlamak için:

```powershell
# Uygulamayı durdurun (Ctrl+C)

# DB'yi sıfırla
docker exec gscrm-db-dev psql -U gscrm -d postgres -c "DROP DATABASE gscrm_dev;" -c "CREATE DATABASE gscrm_dev OWNER gscrm;"

# Uygulamayı tekrar başlat — Flyway tüm migration'ları otomatik çalıştırır
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5800/gscrm_dev"
mvn spring-boot:run
```

---

## Durdurma

```powershell
# Uygulamayı durdur
Ctrl+C

# PostgreSQL container'ını durdur
docker compose -f docker-compose.dev.yml down

# Container + verileri tamamen sil (temiz başlangıç)
docker compose -f docker-compose.dev.yml down -v
```

---

## Sorun Giderme

| Sorun | Çözüm |
|-------|-------|
| `Connection refused: localhost:5432` | `SPRING_DATASOURCE_URL` env variable'ı set edilmemiş. Port `5800` olmalı. |
| `Flyway migration failed` | DB'yi sıfırlayın (yukarıdaki adımlar). |
| `Port 8989 already in use` | Önceki uygulama hâlâ çalışıyor. `netstat -ano | findstr :8989` ile PID'yi bulup `taskkill /PID <pid> /F` ile durdurun. |
| Docker başlamıyor | Docker Desktop'ın çalıştığından emin olun. |
