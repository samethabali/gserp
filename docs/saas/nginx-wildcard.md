# GSCRM Multi-Tenant — Nginx Wildcard Subdomain

## Hedef

Tek `gscrm-app` instance; tenant `{slug}.gscrm.avesitesi.xyz` ile çözülür.

## DNS

```
*.gscrm.avesitesi.xyz  A  <VDS_IP>
gscrm.avesitesi.xyz    A  <VDS_IP>   # landing / platform admin
```

## Nginx site config

```nginx
# /etc/nginx/sites-available/gscrm-multitenant
server {
    listen 443 ssl http2;
    server_name gscrm.avesitesi.xyz *.gscrm.avesitesi.xyz;

    ssl_certificate     /etc/letsencrypt/live/gscrm.avesitesi.xyz/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gscrm.avesitesi.xyz/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8989;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Salon-Slug $subdomain;  # optional; app Host header'dan da okur
    }

    location /actuator/ {
        deny all;
    }
}
```

## SSL (wildcard)

```bash
sudo certbot certonly --nginx -d gscrm.avesitesi.xyz -d '*.gscrm.avesitesi.xyz'
```

## VDS deploy

- Tek container: `127.0.0.1:8989:8989`
- DB: merkezi `postgres` / `shared-db` / `gscrm` database
- `TenantFilter` Host header'dan slug çıkarır

## Local dev

`/etc/hosts` veya `default.localhost:8989` → `app.tenant-header: X-Salon-Slug: default`
