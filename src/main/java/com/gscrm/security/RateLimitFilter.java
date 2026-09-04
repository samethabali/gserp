package com.gscrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * <p>Kapsam yalnızca herkese açık, saldırıya değer uçlardır: giriş, token yenileme,
 * kayıt ve genel randevu API'si. Prod'da nginx {@code limit_req} ilk katman olarak
 * çalışmalıdır; bu filtre ikinci katmandır.
 *
 * <p><b>İstemci kimliği:</b> {@code X-Forwarded-For} başlığına yalnızca istek
 * güvenilen bir vekilden geldiyse itibar edilir. Aksi halde başlık istemci
 * kontrolündedir ve saldırgan her istekte farklı bir sahte IP göndererek sınırı
 * tamamen atlar.
 */
@Slf4j
@Component
// TenantFilter (HIGHEST) ve TenantMdcFilter (+1) sonrasi, guvenlik zincirinden once:
// slug baglami hazir olsun ama pahali kimlik dogrulama islerine hic girilmesin.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Bir pencerede en fazla kaç istek — uç türüne göre. */
    private static final int LOGIN_LIMIT_PER_MINUTE = 8;
    private static final int REGISTER_LIMIT_PER_MINUTE = 5;
    private static final int BOOKING_WRITE_LIMIT_PER_MINUTE = 10;
    private static final int BOOKING_READ_LIMIT_PER_MINUTE = 60;
    /** Doğrulama kodu üretimi/denemesi genel randevu yazma sınırından daha dar olmalı. */
    private static final int OTP_START_LIMIT_PER_MINUTE = 3;
    private static final int OTP_CONFIRM_LIMIT_PER_MINUTE = 6;

    private static final long WINDOW_MILLIS = 60_000L;

    /** Sayaç haritasının sınırsız büyümesini engelleyen üst sınır. */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitFor(request) == 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        int limit = limitFor(request);
        String key = bucketKey(request);
        long now = System.currentTimeMillis();

        if (windows.size() > MAX_TRACKED_KEYS) {
            evictExpired(now);
        }

        Window window = windows.compute(key, (k, existing) ->
                (existing == null || now - existing.startedAt() > WINDOW_MILLIS)
                        ? new Window(now, new AtomicInteger(0))
                        : existing);

        if (window.counter().incrementAndGet() > limit) {
            long retryAfterSeconds = Math.max(1,
                    (WINDOW_MILLIS - (now - window.startedAt())) / 1000);
            log.warn("Hız sınırı aşıldı: {} {} ({} istek/dk sınırı)",
                    request.getMethod(), request.getRequestURI(), limit);
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

    /** İlgili uç için dakikalık sınır; 0 ise bu uç sınırlanmıyor demektir. */
    private int limitFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());

        if (isPost && (uri.equals("/login") || uri.equals("/api/auth/login")
                || uri.equals("/api/auth/refresh") || uri.equals("/customer/login"))) {
            return LOGIN_LIMIT_PER_MINUTE;
        }
        if (isPost && (uri.equals("/api/onboarding/register") || uri.equals("/customer/register"))) {
            return REGISTER_LIMIT_PER_MINUTE;
        }
        // Genel /api/booking dalından önce: aksi hâlde OTP uçları 10/dk'yı miras alırdı.
        if (isPost && uri.equals("/api/booking/verify/start")) {
            return OTP_START_LIMIT_PER_MINUTE;
        }
        if (isPost && uri.equals("/api/booking/verify/confirm")) {
            return OTP_CONFIRM_LIMIT_PER_MINUTE;
        }
        if (uri.startsWith("/api/booking")) {
            return isPost ? BOOKING_WRITE_LIMIT_PER_MINUTE : BOOKING_READ_LIMIT_PER_MINUTE;
        }
        return 0;
    }

    /**
     * Sayaç anahtarı: uç türü + tenant + istemci IP.
     *
     * <p>Giriş uçlarında kullanıcı adı da anahtara girer; böylece tek bir kullanıcıyı
     * hedefleyen deneme, o IP'nin diğer trafiğini etkilemeden ayrı sayılır.
     */
    private String bucketKey(HttpServletRequest request) {
        String salon = com.gscrm.tenant.TenantContext.getSlug();
        return (salon != null ? salon : "-") + '|' + request.getRequestURI() + '|'
                + clientIpResolver.resolve(request);
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
