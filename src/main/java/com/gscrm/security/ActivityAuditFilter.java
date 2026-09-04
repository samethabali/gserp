package com.gscrm.security;

import com.gscrm.service.ActivityEventService;
import com.gscrm.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servis katmanının ayrıca kaydetmediği her yazma isteği için kütüğe bir satır yazar.
 *
 * <p>Üç davranışı değişti:
 * <ul>
 *   <li>Başarısız istekler artık atlanmıyor. Önceden {@code status >= 400} olan her
 *       şey sessizce düşüyordu; yetkisiz erişim denemeleri kütükte hiç görünmüyordu.</li>
 *   <li>Servis katmanı bu istek için zaten anlamlı bir kayıt yazdıysa jenerik satır
 *       yazılmıyor — randevu, müşteri ve ödeme işlemleri iki kez loglanıyordu.</li>
 *   <li>IP {@link ClientIpResolver} ile çözülüyor; {@code getRemoteAddr()} nginx
 *       arkasında her kayda vekilin adresini yazıyordu.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ActivityAuditFilter extends OncePerRequestFilter {

    private static final Pattern CUSTOMER_PATH = Pattern.compile("^/api/customers/(\\d+)");

    private final ActivityEventService activityEventService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        // Kimlik uçlarını AuthEventLogger daha anlamlı biçimde kaydediyor.
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/webhooks/")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (Boolean.TRUE.equals(request.getAttribute(ActivityEventService.REQUEST_ATTR_RECORDED))) {
            return;
        }
        if (TenantContext.getSalonId() == null) {
            return;
        }

        int status = response.getStatus();
        String path = request.getRequestURI();
        Long customerId = null;
        Matcher matcher = CUSTOMER_PATH.matcher(path);
        if (matcher.find()) {
            customerId = Long.parseLong(matcher.group(1));
        }

        activityEventService.recordHttp(
                request.getMethod(),
                customerId,
                request.getMethod() + " " + path,
                status,
                clientIpResolver.resolve(request));
    }
}
