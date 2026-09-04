package com.gscrm.security;

import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Online randevu isteklerine IP başına <b>günlük</b> tavan.
 *
 * <p>{@code RateLimitFilter} dakikalık ani yükü keser ama gün boyunca dakikada bir
 * istek gönderen bir betiği durdurmaz. Bu sayaç onu kapatır.
 *
 * <p>Dürüst sınır: bellekte tutulur, yani süreç yeniden başlayınca sıfırlanır ve tek
 * instance varsayar — {@code RateLimitFilter} zaten aynı ödünleşimi yapıyor. Dağıtık
 * bir botu da durdurmaz; bu, doğrulama kapalıyken devrede olan ücretsiz kademedir.
 * Ücretli kademe SMS doğrulama bayrağıdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingAbuseGuard {

    private final ClientIpResolver clientIpResolver;

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private volatile LocalDate countersDay = LocalDate.now();

    /**
     * Bu IP bugünkü kotasını doldurdu mu? Doldurduysa {@code false} döner ve
     * istek reddedilmelidir. Çağrı sayacı da artırır.
     */
    public boolean tryConsume(HttpServletRequest request, int dailyLimit) {
        if (dailyLimit <= 0) return true;
        rollOverIfNewDay();

        String slug = TenantContext.getSlug();
        String key = (slug != null ? slug : "-") + '|' + clientIpResolver.resolve(request);

        int used = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (used > dailyLimit) {
            log.warn("Randevu isteği günlük IP tavanını aştı: {} ({} istek/gün sınırı)", key, dailyLimit);
            return false;
        }
        return true;
    }

    private void rollOverIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(countersDay)) {
            synchronized (this) {
                if (!today.equals(countersDay)) {
                    counters.clear();
                    countersDay = today;
                }
            }
        }
    }

    /** Gün dönümünü kaçıran süreçler için güvenlik ağı (RetentionJob ile aynı saat dilimi). */
    @Scheduled(cron = "0 30 3 * * *")
    public void sweep() {
        rollOverIfNewDay();
    }
}
