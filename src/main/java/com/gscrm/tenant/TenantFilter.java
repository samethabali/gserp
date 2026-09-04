package com.gscrm.tenant;

import com.gscrm.model.Salon;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * İsteği bir salona (kiracıya) bağlar.
 *
 * <p>Eskiden çözümleme beş kaynaktan yapılıyordu — başlık, bir yıllık çerez,
 * alt alan adı, {@code *.localhost} ve sessiz {@code default} yedeği — ve hiçbiri
 * diğeriyle karşılaştırılmıyordu. Bunun iki somut sonucu vardı: şube değiştiren bir
 * kullanıcının çerezi alt alan adını eziyor, yani URL kiracıyı yanlış gösteriyordu;
 * tanınmayan bir alt alan adında ise CSS/JS dahil her istek JSON 404 dönüyordu.
 * Üstelik alt alan adı üretimde hiç çalışmıyordu — wildcard DNS de sertifika da yok.
 *
 * <p>Artık tek bir kesin sıra var ve her adım açıkça beyan edilmiş bir kaynağa dayanır:
 *
 * <ol>
 *   <li>Bearer JWT'deki salonId — API istemcileri</li>
 *   <li>Oturumdaki kimlikli salon — giriş anında yazılır</li>
 *   <li>Adreste açıkça belirtilen slug — /b/{slug}, ?salonSlug=, X-Salon-Slug</li>
 *   <li>Anonim ziyaretçinin daha önce /b/{slug} ile seçtiği salon</li>
 * </ol>
 *
 * <p>Kimlikli bir istekte açıkça verilen slug kimliğin salonuyla uyuşmuyorsa istek
 * reddedilir; daha önce herkes başlık göndererek başka kiracının public yüzeyine
 * geçebiliyordu.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private static final String SALON_SLUG_HEADER = "X-Salon-Slug";
    private static final String SALON_SLUG_PARAM = "salonSlug";
    private static final Pattern PUBLIC_BOOKING_PATH =
            Pattern.compile("^/b/([a-z0-9][a-z0-9-]{1,62})(/.*)?$");

    /** Kiracı bağlamı gerekmeyen yollar. Statik dosyalar dahil — aksi hâlde giriş sayfası bile çizilemiyordu. */
    private static final List<String> BYPASS_PREFIXES = List.of(
            "/actuator",
            "/api/auth",
            "/api/platform",
            "/api/onboarding/register",
            "/css/",
            "/js/",
            "/images/",
            "/webjars/");

    private static final List<String> BYPASS_EXACT = List.of(
            "/login",
            "/logout",
            "/error",
            "/favicon.ico",
            "/onboarding/wizard");

    private final SalonRepository salonRepository;
    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return BYPASS_EXACT.contains(path)
                || BYPASS_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/platform") || path.startsWith("/platform/")) {
            TenantContext.setPlatformBypass(true);
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        Long authSalonId = resolveAuthenticatedSalonId(request);
        String explicitSlug = resolveExplicitSlug(request);

        Salon salon;
        if (authSalonId != null) {
            salon = salonRepository.findById(authSalonId).filter(Salon::isActive).orElse(null);
            if (salon != null && explicitSlug != null && !explicitSlug.equals(salon.getSlug())) {
                // Kimlikli istek başka bir kiracıyı işaret ediyor: sessizce birini seçmek yerine reddet.
                deny(response, HttpStatus.FORBIDDEN, "İstek başka bir işletmeye ait");
                return;
            }
        } else if (explicitSlug != null) {
            salon = salonRepository.findBySlugAndActiveTrue(explicitSlug).orElse(null);
        } else {
            salon = resolvePublicSelection(request);
        }

        if (salon == null) {
            if ("/booking".equals(path) && explicitSlug == null) {
                filterChain.doFilter(request, response);
                return;
            }
            handleUnresolved(request, response, explicitSlug);
            return;
        }

        TenantContext.setSalonId(salon.getId());
        TenantContext.setOrgId(salon.getOrganizationId());
        TenantContext.setSlug(salon.getSlug());
        TenantContext.setShowcase(salon.isShowcase());
        if (salon.isShowcase()) {
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** JWT veya oturumdaki kimlikli salon; ikisi de yoksa null. */
    private Long resolveAuthenticatedSalonId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long fromToken = jwtService.extractSalonId(header.substring(7));
                if (fromToken != null) {
                    return fromToken;
                }
            } catch (Exception e) {
                // Geçersiz token burada değil, JwtAuthenticationFilter'da ele alınır.
                log.debug("Tenant çözümlemesinde token okunamadı: {}", e.getMessage());
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (Long) session.getAttribute(TenantContext.SESSION_AUTH_SALON_ID);
        }
        return null;
    }

    /** Adreste açıkça belirtilen slug: /b/{slug} yolu, salonSlug parametresi veya X-Salon-Slug başlığı. */
    private String resolveExplicitSlug(HttpServletRequest request) {
        Matcher matcher = PUBLIC_BOOKING_PATH.matcher(request.getRequestURI());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        String param = request.getParameter(SALON_SLUG_PARAM);
        if (param != null && !param.isBlank()) {
            return normalize(param);
        }
        String header = request.getHeader(SALON_SLUG_HEADER);
        if (header != null && !header.isBlank()) {
            return normalize(header);
        }
        return null;
    }

    private Salon resolvePublicSelection(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Long selected = (Long) session.getAttribute(TenantContext.SESSION_PUBLIC_SALON_ID);
        if (selected == null) {
            return null;
        }
        return salonRepository.findById(selected).filter(Salon::isActive).orElse(null);
    }

    /**
     * Kiracı çözülemedi. API makine okunur hata alır, sayfalar girişe yönlenir —
     * eskiden ikisi de ham JSON 404 alıyordu ve yanıt slug'ın varlığını ele veriyordu.
     */
    private void handleUnresolved(HttpServletRequest request, HttpServletResponse response, String explicitSlug)
            throws IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            HttpStatus status = explicitSlug != null ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            deny(response, status, explicitSlug != null ? "İşletme bulunamadı" : "İşletme belirtilmedi");
            return;
        }
        if (path.startsWith("/b/")) {
            response.sendRedirect("/booking");
            return;
        }
        response.sendRedirect("/login");
    }

    private void deny(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }

    private String normalize(String slug) {
        return slug.trim().toLowerCase();
    }
}
