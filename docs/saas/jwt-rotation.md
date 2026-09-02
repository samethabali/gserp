# JWT Secret Rotation Playbook

1. VDS `.env` içinde yeni `JWT_SECRET` üretin (min 256-bit, base64).
2. Eski secret'ı `JWT_SECRET_PREVIOUS` olarak 24 saat tutun (opsiyonel dual-verify yoksa kısa bakım penceresi).
3. `docker compose up -d` ile uygulamayı yeniden başlatın.
4. Tüm oturumlar yeniden giriş gerektirir — kullanıcılara duyuru yapın.
5. 24 saat sonra `JWT_SECRET_PREVIOUS` kaldırın.

WhatsApp token encryption `app.jwt.secret` türetir; rotation sonrası salon token'larını Ayarlar'dan yeniden kaydedin.
