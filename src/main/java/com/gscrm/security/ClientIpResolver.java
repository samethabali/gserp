package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * İstemci IP'sini <b>tek</b> yerde çözer.
 *
 * <p>Bu değere bağlı olan şeyler: giriş denemesi sınırı, kayıt sınırı, OTP
 * sınırı, randevu yazma sınırı ve günlük randevu kötüye kullanım sayacı. Yanlış
 * çözülen bir IP bunların hepsini birden ya işlemez ya da aşırı katı hâle
 * getirir; bu yüzden mantığın ikinci bir kopyası yok.
 *
 * <p><b>Temel kural:</b> İstek doğrudan geldiyse hiçbir başlığa bakılmaz —
 * başlıklar tamamen istemci kontrolündedir. Yalnızca istek güvenilen bir
 * vekilden geldiyse başlıklara itibar edilir.
 *
 * <p><b>Zincirin hangi ucuna bakılır:</b> Vekiller {@code X-Forwarded-For}
 * başlığına <b>ekleme</b> yapar, üzerine yazmaz. İstemci kendi isteğine
 * {@code X-Forwarded-For: 9.9.9.9} koyarsa vekil bunu koruyup kendi gördüğü
 * adresi sona ekler. Yani <b>baştaki halka istemcinin uydurduğu olabilir</b>;
 * güvenilir olan, bize en yakın vekilin yazdığı <b>son</b> halkadır. Eskiden
 * baştaki halka alınıyordu ve bu, her istekte farklı bir sahte adres göndererek
 * yukarıdaki bütün sayaçların atlanabilmesi demekti.
 *
 * <p><b>Cloudflare:</b> Site Cloudflare arkasında ve zincir
 * {@code istemci -> Cloudflare -> nginx -> uygulama} şeklinde; son halka
 * Cloudflare'in kendi adresi olurdu, yani bütün ziyaretçiler tek bir sayaca
 * düşerdi. Cloudflare gerçek istemci adresini {@code CF-Connecting-IP}
 * başlığına yazar ve istemcinin gönderdiğini <b>ezer</b>; bu yüzden o başlık
 * öncelikli.
 *
 * <p><b>Kalan açık (sunucu tarafı):</b> Origin adresini bilen biri Cloudflare'i
 * atlayıp nginx'e doğrudan bağlanırsa bu başlıkları kendisi uydurabilir. Bunun
 * çözümü uygulamada değil, nginx'in yalnızca Cloudflare adres bloklarından
 * bağlantı kabul etmesindedir.
 */
@Component
public class ClientIpResolver {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    /** Cloudflare'in gerçek istemci adresini yazdığı başlık; istemci sahteleyemez. */
    private static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr != null ? remoteAddr : "unknown";
        }

        String cloudflare = header(request, CLOUDFLARE_HEADER);
        if (cloudflare != null) {
            return cloudflare;
        }

        String forwarded = header(request, FORWARDED_HEADER);
        if (forwarded != null) {
            String[] hops = forwarded.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (!nearest.isEmpty()) {
                return nearest;
            }
        }

        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Vekil güvenilir mi? Uygulama loopback'e bağlanıp nginx arkasında çalıştığı için
     * loopback varsayılan olarak güvenilirdir; ek vekiller
     * {@code app.security.trusted-proxies} ile tanımlanır.
     */
    public boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if (LOOPBACK.contains(remoteAddr) || trustedProxies.contains(remoteAddr)) {
            return true;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
