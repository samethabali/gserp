# SSH ile bilgi toplama görevi

Bu dosya, **SSH erişimi olan makinedeki asistan** için yazıldı. Görev tek şey:
üretim sunucusundan üç bilgiyi **okuyup** repoya getirmek.

---

## Durum — okumadan komut çalıştırma

- `https://gscrm.avesitesi.xyz` **canlıda ve pilot müşterilere davet kodları
  gönderildi.** Şu anda içeride gerçek kullanıcı olabilir.
- Üretim deploy'ları **bilerek donduruldu.** Yeni işler `staging` dalında
  yapılıyor; `production-ready`'ye push edilen her şey otomatik canlıya iniyor.
- Uygulama **tek konteynerde** çalışıyor ve oturumlar **bellekte**. Konteyneri
  yeniden başlatmak ~90 sn kesinti + **giriş yapmış herkesin düşmesi** demek.

## Kesin kurallar

| Yasak | Neden |
|---|---|
| `docker restart`, `docker compose up/down/build`, `systemctl restart` | Müşteriyi düşürür |
| `git pull` / `git checkout` (sunucudaki repoda) | Bir sonraki deploy'u sürükler |
| Herhangi bir dosyayı **değiştirmek** | Bu görev salt okuma |
| `.env` içeriğini okumak/paylaşmak | JWT secret, DB parolası, şifreleme anahtarı |
| `docker compose config` | **`.env`'deki parolaları çözüp düz metin basar** |
| `production-ready` dalına push | Otomatik deploy tetikler |

Bir komut yukarıdakilerden birine giriyorsa **çalıştırma, sor.**

---

## Toplanacak üç şey

### 1. Üretim compose dosyası (asıl ihtiyaç)

```bash
cat /home/gserp/gserp/docker-compose.prod.yml
```

Bu dosya repoda yok, yalnızca sunucuda ve elle bakılıyor. Sıfır kesintili deploy
çalışması (iki konteyner) doğrudan bu dosyayı değiştiriyor; elimizde olmadan
yazılamıyor.

### 2. nginx site yapılandırması

```bash
sudo nginx -T 2>/dev/null | sed -n '/server_name .*gscrm/,/^}/p'
```

Çalışmazsa dosyayı bul ve oku:

```bash
sudo grep -rl "gscrm.avesitesi.xyz" /etc/nginx/
sudo cat /etc/nginx/sites-available/<bulunan-dosya>
```

İki konteyner için buraya `upstream` bloğu yazılacak. Şu an tek bir
`proxy_pass http://127.0.0.1:8989` olduğunu biliyoruz; tamamını görmek gerekiyor.

### 3. Kaynak durumu

```bash
free -h
df -h /
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}'
```

Son deploy log'unda **30G diskin 4.7G'si boştu (%84 dolu)** ve imaj build'i bu
kutuda yapılıyor. İkinci bir konteynerin sığıp sığmayacağını bu belirleyecek.

---

## Çıktıyı nasıl geri getir

**Repo bu makinede varsa** — tercih edilen yol:

```bash
git fetch origin
git checkout staging
git pull

mkdir -p docs/sunucu
# 1. dosyayı olduğu gibi kopyala:
#    /home/gserp/gserp/docker-compose.prod.yml  ->  docker-compose.prod.yml (repo kökü)
# 2. nginx çıktısını  docs/sunucu/nginx-gscrm.conf  olarak kaydet
# 3. kaynak çıktısını docs/sunucu/kaynak-durumu.txt olarak kaydet

git add -A
git commit -m "chore: üretim compose, nginx ve kaynak durumunu repoya al"
git push origin staging
```

`staging` dalına push **deploy tetiklemez** — o dalda yalnızca "Staging CI"
çalışır. `production-ready`'ye push etme.

**Repo yoksa:** üç çıktıyı düz metin olarak kullanıcıya ver, o taşısın.

---

## Yapıştırmadan önce maskeleme

Compose dosyası normalde `${DEĞİŞKEN}` referansları taşır, değerleri değil.
Yine de göndermeden önce göz gezdir; şu kalıplardan biri **düz metin** görünüyorsa
değerini `***` ile değiştir ve bunu belirt:

- `PASSWORD`, `SECRET`, `KEY`, `TOKEN` içeren satırlar
- `postgres://kullanıcı:parola@...` biçiminde bağlantı adresleri

## İşi bitirince

Şunu rapor et:

1. Üç çıktı da alındı mı, alınamayan varsa neden
2. Sunucuda **hiçbir şey değiştirilmediği**
3. Diskte kaç GB boş yer olduğu (ikinci konteyner kararı buna bağlı)
4. Kaç konteyner çalışıyor ve hangi portlarda

Bundan sonraki adımlar `docs/YAPILACAKLAR.md` içinde — Aşama 0 (compose'u repoya
al), Aşama 1 (build'i CI'a taşı), Aşama 3 (iki konteyner + sıralı deploy).
**Bu görev yalnızca Aşama 0'ın veri toplama kısmı; devamını yapma.**
