package com.gscrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kaba kuvvet ve kötüye kullanıma karşı istek hızı sınırı.
 *
 * <p>Prod'da nginx {@code limit_req} ilk katman olarak çalışmalıdır; bu filtre
 * ikinci katmandır.
 *
 * <p><b>İstemci kimliği:</b> {@code X-Forwarded-For} başlığına yalnızca istek
 * güvenilen bir vekilden geldiyse itibar edilir. Aksi halde başlık istemci
 * kontrolündedir ve saldırgan her istekte farklı bir sahte IP göndererek sınırı
 * tamamen atlar.
 *
 * <p><b>Sayacın neye bağlandığı uca göre değişir</b> ({@link Scope}); bu, sınırın
 * kendisinden daha önemli bir tasarım kararı. Yanlış seçilen bir anahtar ya
 * korumayı işe yaramaz hâle getirir ya da gerçek kullanıcıları birbirine kilitler.
 */
@Slf4j
@Component
// TenantFilter (HIGHEST) ve TenantMdcFilter (+1) sonrasi, guvenlik zincirinden once:
// slug baglami hazir olsun ama pahali kimlik dogrulama islerine hic girilmesin.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    /** Sayaç haritasının sınırsız büyümesini engelleyen üst sınır. */
    private static final int MAX_TRACKED_KEYS = 50_000;

    /** Sayacın neye bağlanacağı. */
    private enum Scope {
        /**
         * İstemci IP'si. Kimlik denemesi ve randevu <b>yazma</b> gibi uçlarda
         * zorunlu: sayaç oturuma bağlansaydı, çerezini silen bir saldırgan her
         * seferinde sıfırdan başlar ve sınır hiç işlemezdi.
         */
        CLIENT_IP,
        /**
         * Önce oturum, oturum yoksa IP.
         *
         * <p>Okuma uçlarında doğru olan bu. IP'ye bağlanan bir sayaç, tek bir
         * NAT'ın arkasındaki kullanıcıları aynı bütçeye sokar: aynı ofisteki beş
         * personel ya da aynı mobil operatörün CGNAT'ı üzerinden gelen onlarca
         * müşteri birbirini kilitler. Çerez tutmayan bir istemci (yani otomatik
         * bir tarayıcı değil, bir betik) oturumsuz kaldığı için yine IP kovasına
         * düşer; koruma kaybolmaz.
         */
        VISITOR
    }

    /**
     * Bir uç ailesinin sınırı.
     *
     * @param family sayaç anahtarının ön eki — aynı ailedeki uçlar tek bütçeyi paylaşır
     */
    private record Limit(String family, int perMinute, Scope scope) {}

    private static final Limit LOGIN = new Limit("giris", 8, Scope.CLIENT_IP);
    private static final Limit REGISTER = new Limit("kayit", 5, Scope.CLIENT_IP);
    /** Doğrulama kodu üretimi/denemesi genel randevu yazma sınırından daha dar olmalı. */
    private static final Limit OTP_START = new Limit("otp-baslat", 3, Scope.CLIENT_IP);
    private static final Limit OTP_CONFIRM = new Limit("otp-dogrula", 6, Scope.CLIENT_IP);
    private static final Limit BOOKING_WRITE = new Limit("randevu-yazma", 10, Scope.CLIENT_IP);

    /**
     * Randevu sayfasının okuma uçları.
     *
     * <p>Ziyaretçi başına tek bütçe: sayfa açılışında ayarlar, hizmetler ve
     * personel çekiliyor, sonra seçilen her gün için müsait saatler. Gün gün
     * gezinen bir müşteri dakikada rahatlıkla otuz istek atabilir; 120 bunun
     * üstünde kalırken kaçak bir döngüyü hâlâ durduruyor.
     */
    private static final Limit BOOKING_READ = new Limit("randevu-okuma", 120, Scope.VISITOR);

    /**
     * Diğer tüm API uçları için genel taban.
     *
     * <p>Değer bilerek yüksek: dakikada 300 istek, saniyede beşe denk geliyor.
     * Yoğun bir resepsiyonistin takvimi çevirip randevu açması bunun çok altında
     * kalır — amaç kullanıcıyı yavaşlatmak değil, kaçak döngüyü durdurmak.
     */
    private static final Limit GENERAL_API = new Limit("api", 300, Scope.VISITOR);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Limit limit = limitFor(request);
        String key = bucketKey(request, limit);
        long now = System.currentTimeMillis();

        if (windows.size() > MAX_TRACKED_KEYS) {
            evictExpired(now);
        }

        Window window = windows.compute(key, (k, existing) ->
                (existing == null || now - existing.startedAt() > WINDOW_MILLIS)
                        ? new Window(now, new AtomicInteger(0))
                        : existing);

        if (window.counter().incrementAndGet() > limit.perMinute()) {
            long retryAfterSeconds = Math.max(1,
                    (WINDOW_MILLIS - (now - window.startedAt())) / 1000);
            log.warn("Hız sınırı aşıldı: {} {} ({} istek/dk sınırı)",
                    request.getMethod(), request.getRequestURI(), limit.perMinute());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Çok fazla deneme. Lütfen bir dakika bekleyin.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** İlgili uç için sınır; {@code null} ise bu uç sınırlanmıyor demektir. */
    private Limit limitFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());

        // Müşteri portalı formları sayfa yoluna değil /api/auth/customer/* ucuna POST
        // ediyor; sınır yalnızca sayfa yollarında tanımlıyken bu iki uç tamamen
        // sınırsızdı, yani müşteri parolası serbestçe denenebiliyordu.
        if (isPost && (uri.equals("/login") || uri.equals("/api/auth/login")
                || uri.equals("/api/auth/refresh")
                || uri.equals("/customer/login") || uri.equals("/api/auth/customer/login"))) {
            return LOGIN;
        }
        if (isPost && (uri.equals("/api/onboarding/register")
                || uri.equals("/customer/register") || uri.equals("/api/auth/customer/register"))) {
            return REGISTER;
        }
        // Genel /api/booking dalından önce: aksi hâlde OTP uçları 10/dk'yı miras alırdı.
        if (isPost && uri.equals("/api/booking/verify/start")) {
            return OTP_START;
        }
        if (isPost && uri.equals("/api/booking/verify/confirm")) {
            return OTP_CONFIRM;
        }
        if (uri.startsWith("/api/booking")) {
            return isPost ? BOOKING_WRITE : BOOKING_READ;
        }
        // Yukarıdakilerin dışında kalan her API ucu genel tabana tabi.
        if (uri.startsWith("/api/")) {
            return GENERAL_API;
        }
        return null;
    }

    /**
     * Sayaç anahtarı.
     *
     * <p>{@link Scope#VISITOR} ailelerinde aile adı + ziyaretçi. Aile başına
     * <b>tek</b> bütçe olması önemli: URI başına ayrı sayılsaydı, üç yüz farklı
     * uca giden bir döngü hiçbir sınıra takılmazdı.
     *
     * <p>Kimlik yerine oturum kullanılıyor çünkü bu filtre Spring Security'den
     * ÖNCE çalışıyor ve SecurityContext henüz dolu değil.
     *
     * <p>{@link Scope#CLIENT_IP} ailelerinde anahtar kiracı + uç + IP olarak
     * kalıyor; bu uçlarda oturum tazelenerek sınırın atlanabilmesi kabul edilemez.
     */
    private String bucketKey(HttpServletRequest request, Limit limit) {
        if (limit.scope() == Scope.VISITOR) {
            HttpSession session = request.getSession(false);
            String visitor = session != null
                    ? "s:" + session.getId()
                    : "i:" + clientIpResolver.resolve(request);
            return limit.family() + '|' + visitor;
        }
        String salon = com.gscrm.tenant.TenantContext.getSlug();
        if (salon == null || salon.isBlank()) {
            salon = request.getHeader("X-Salon-Slug");
        }
        if (salon == null || salon.isBlank()) {
            salon = request.getParameter("salonSlug");
        }
        return (salon != null && !salon.isBlank() ? salon.trim().toLowerCase() : "-") + '|' + request.getRequestURI() + '|'
                + clientIpResolver.resolve(request);
    }

    public void reset() {
        windows.clear();
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt() > WINDOW_MILLIS);
        if (windows.size() > MAX_TRACKED_KEYS) {
            // Hepsi tazeyse (yoğun saldırı) haritayı tamamen boşaltmak, belleği
            // korumak için sınırın kısa süre gevşemesinden daha iyidir.
            log.warn("Hız sınırı sayaç haritası doldu; sıfırlanıyor");
            windows.clear();
        }
    }

    private record Window(long startedAt, AtomicInteger counter) {
    }
}
