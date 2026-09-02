# GSCRM — Demo Rehberi

Arkadaşlarla yerel ortamda arayüz ve iş akışlarını denemek için kısa rehber.

## Gereksinimler

- Java 21, Maven 3.9+, Docker Desktop

## Başlatma

Proje kök dizininde PowerShell:

```powershell
.\start-dev.ps1
```

Script PostgreSQL’i (port **5800**) başlatır ve uygulamayı **8989** portunda açar.

Manuel başlatma: [DEVELOPMENT.md](../DEVELOPMENT.md)

## Giriş

| Alan | Değer |
|------|--------|
| URL | http://localhost:8989/login |
| Kullanıcı | `admin` |
| Parola | `admin` |
| Salon (tenant) | `default` (lokal dev otomatik) |

Diğer demo kullanıcıları (aynı parola `admin`):

| Kullanıcı | Rol | Tenant slug |
|-----------|-----|-------------|
| `merve` | Resepsiyon | `default` |
| `ayse` | Uzman | `default` |
| `fatma` | Uzman | `default` |
| `admin@guzellik-atolyesi` | Şube yöneticisi | `guzellik-atolyesi` |
| `owner@belleza` | Org sahibi | `belleza-kadikoy` (header) |
| `mgr-kadikoy` | Şube yöneticisi | `belleza-kadikoy` |
| `mgr-besiktas` | Şube yöneticisi | `belleza-besiktas` |

> Pilot senaryolar dev profilinde `PilotScenarioSeeder` ile yüklenir. Lokalde tenant header: `X-Salon-Slug: <slug>` veya subdomain.

## URL’ler

| Sayfa | Adres |
|-------|--------|
| Takvim | http://localhost:8989/ |
| Dashboard | http://localhost:8989/dashboard |
| Müşteriler | http://localhost:8989/customers |
| Online booking (public) | http://localhost:8989/booking |
| Salon ayarları | http://localhost:8989/settings |
| Abonelik | http://localhost:8989/settings/billing |
| Kurulum sihirbazı | http://localhost:8989/onboarding/setup |
| Yeni salon kaydı (public) | http://localhost:8989/onboarding/wizard |

## 15 dakikalık demo akışı

### 1. Takvim — randevu oluşturma

1. Giriş yap → Takvim açılır.
2. Boş bir saate tıkla veya **Yeni Randevu**.
3. **Son müşteriler** chip’lerinden birine tıkla veya isim/telefon ara.
4. Uzman + hizmet seç, **Kaydet**.
5. İsteğe bağlı: **Son randevunun aynısı** ile tekrarlayan işlem.

### 2. Dashboard — tahsilat

1. **Dashboard** sekmesi.
2. Bugünkü randevuda **✅** butonuna tıkla.
3. **Tahsilat Al** modalında tutar ve ödeme yöntemi (nakit/kart) seç.
4. **Kaydet ve Tamamla** → randevu tamamlanır, ödeme kaydı oluşur.

### 3. Müşteriler

1. **Müşteriler** → listeden bir müşteri seç.
2. Bakiye, ödeme geçmişi ve randevu geçmişini incele.

### 4. Public booking

1. Yeni sekmede http://localhost:8989/booking (giriş gerekmez).
2. Hizmet, uzman, tarih seç → randevu isteği gönder.
3. Takvimde **PENDING_APPROVAL** olarak görünür; onaylayabilirsin.

### 5. Ayarlar & abonelik

1. **Salon Ayarları** → white-label (salon adı, renk).
2. **Abonelik** → plan, şube/kullanıcı kotası, trial durumu.

### 6. Ürünler ve kampanyalar

- **Ürünler**: stok listesi, kategori filtresi (koyu tema dropdown).
- **Kampanyalar**: kupon ve sadakat eşikleri.

## Arkadaşların erişimi

### Aynı Wi‑Fi / LAN (önerilen demo)

1. Bilgisayarında uygulama çalışırken Windows Güvenlik Duvarı’nda **8989** TCP girişine izin ver.
2. IP adresini öğren: `ipconfig` → IPv4 (ör. `192.168.1.42`).
3. Arkadaşlar tarayıcıda: `http://192.168.1.42:8989` — giriş `admin` / `admin`.

### İnternet üzerinden (VDS)

Canlı ortam:

| Sayfa | URL |
|-------|-----|
| Giriş | https://gscrm.avesitesi.xyz/login |
| Demo salon (subdomain) | https://default.gscrm.avesitesi.xyz/login |
| Public booking | https://default.gscrm.avesitesi.xyz/booking |

Deploy: `production-ready` push sonrası GitHub Actions **Deploy to VPS** veya [deploy-vps.md](deploy-vps.md).

> Prod'da demo verisi yoksa `/onboarding/wizard` ile yeni tenant açın veya mevcut admin bilgilerini kullanın.

Ürünleştirme planı: [PRODUCT_ROADMAP.md](PRODUCT_ROADMAP.md).

## Sorun giderme

| Sorun | Çözüm |
|-------|--------|
| `admin` / `admin` çalışmıyor | DB’yi sıfırla (aşağı) veya parola eski `admin123` ise yeni kurulum yap |
| Sayfa login’e atıyor | Çıkış yap, tekrar giriş; API 403 ise tarayıcı önbelleğini temizle |
| Port 8989 dolu | `netstat -ano \| findstr :8989` → PID ile `taskkill /F /PID <pid>` |
| Eski CSS/JS | **Ctrl+Shift+R** (hard refresh) |
| Flyway / DB hatası | Temiz DB: |

```powershell
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
.\start-dev.ps1
```

| Connection refused 5432 | `SPRING_DATASOURCE_URL` port **5800** olmalı (`start-dev.ps1` bunu ayarlar) |

## Durdurma

- Uygulama: terminalde **Ctrl+C**
- Veritabanı: `docker compose -f docker-compose.dev.yml down`
