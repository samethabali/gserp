# GSCRM Multi-Tenant — Tek Domain, Yol Tabanlı Kiracı

Bu dosya `nginx-wildcard.md`'nin yerini alır. Wildcard subdomain modeli üründen
tamamen kaldırıldı; sebebi tek cümleyle: **hiç çalışmıyordu.**

- `*.gscrm.avesitesi.xyz` DNS'te tanımlı değildi.
- Cloudflare ücretsiz Universal SSL ikinci seviye wildcard'ı kapsamıyor.
- nginx `server_name` yalnızca apex'i dinliyordu.
- Eski rehberdeki `proxy_set_header X-Salon-Slug $subdomain;` satırı tanımsız bir
  değişken kullanıyordu ve `nginx -t` testinden geçmiyordu.

Sonuç: sihirbaz kullanıcıya `slug.BASE_DOMAIN` adresini vaat ediyor, o adres
hiçbir zaman açılmıyordu.

## Model

Tek alan adı: **https://gscrm.avesitesi.xyz**

| Yüzey | Adres | Kiracı nereden çözülür |
|---|---|---|
| Personel girişi | `/login` | Oturum (`TENANT_SALON_ID`), girişte yazılır |
| Uygulama | `/`, `/customers`, … | Oturum |
| API (token'lı) | `/api/**` | `Authorization: Bearer` içindeki `salonId` claim'i |
| Public randevu | `/b/{slug}` | Adresteki slug |
| Public API | `/api/booking/**` | `salonSlug` parametresi veya `X-Salon-Slug` başlığı |
| Platform paneli | `/platform/**` | Kiracı yok (bypass) |

Kimlikli bir istekte açıkça verilen slug, oturum/JWT'deki salonla uyuşmuyorsa
istek reddedilir (HTTP 403). Önceden herkes başlık göndererek başka kiracının
public yüzeyine geçebiliyordu.

## nginx

Wildcard yok, tek `server_name`. Sunucudaki 16 sitenin tamamı ortak
`/etc/ssl/cloudflare/origin-cert.pem` kullanıyor ve hepsi `*.avesitesi.xyz`
altında olduğu için **ek sertifika işi yoktur**.

```nginx
# /etc/nginx/sites-available/gscrm
server {
    listen 443 ssl http2;
    server_name gscrm.avesitesi.xyz;

    ssl_certificate     /etc/ssl/cloudflare/origin-cert.pem;
    ssl_certificate_key /etc/ssl/cloudflare/origin-key.pem;

    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8989;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws-calendar/ {
        proxy_pass http://127.0.0.1:8989;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host       $host;
        proxy_read_timeout 3600s;
    }
}
```

`X-Salon-Slug` başlığı nginx tarafından **üretilmez**. Uygulama onu yalnızca
istemcinin açıkça gönderdiği durumda okur ve kimlikli isteklerde oturumla
karşılaştırır.

### Eski domain'i kırma

`gserp.avesitesi.xyz` altındaki linkler dolaşımda; blok kaldırılmaz, yönlendirilir:

```nginx
server {
    listen 443 ssl http2;
    server_name gserp.avesitesi.xyz;

    ssl_certificate     /etc/ssl/cloudflare/origin-cert.pem;
    ssl_certificate_key /etc/ssl/cloudflare/origin-key.pem;

    return 301 https://gscrm.avesitesi.xyz$request_uri;
}
```

### Geçiş adımları (sunucuda, tek seferlik)

```bash
cp /etc/nginx/sites-available/gserp /etc/nginx/sites-available/gscrm
# server_name gscrm.avesitesi.xyz; yap
ln -s /etc/nginx/sites-available/gscrm /etc/nginx/sites-enabled/gscrm
# eski blogu yukaridaki 301 ile degistir
nginx -t && systemctl reload nginx
```

## .env

⚠️ Deploy workflow'undaki `ensure_env` **yalnızca eksik olan anahtarı ekler,
mevcut değeri asla değiştirmez** (`deploy.yml`). Bu yüzden aşağıdaki düzeltmeler
`.env` üzerinde elle yapılmalıdır:

```bash
# /home/gserp/gserp/.env
APP_PUBLIC_BASE_URL=https://gscrm.avesitesi.xyz
APP_CORS_ALLOWED_ORIGINS=https://gscrm.avesitesi.xyz
GSCRM_PLATFORM_ADMIN_USERNAME=platform
GSCRM_PLATFORM_ADMIN_PASSWORD=<güçlü-parola>

# Silinecek — artık okunmuyor:
# APP_TENANT_BASE_DOMAIN=...
```

`APP_PUBLIC_BASE_URL` kiracı **çözmez**. Yalnızca paylaşılabilir link üretir:
davet linki (`/onboarding/wizard?code=…`) ve işletmenin randevu linki (`/b/{slug}`).
Yanlış olması 404'e değil, yalnızca yanlış görünen bir bağlantıya yol açar.

## Doğrulama

```bash
curl -sI https://gscrm.avesitesi.xyz/actuator/health   # 200
curl -sI https://gserp.avesitesi.xyz/login             # 301 -> gscrm...
curl -sI https://test.gscrm.avesitesi.xyz/             # cozulmez; urun bu adresi hic uretmez
```
