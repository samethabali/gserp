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

    /**
     * {@code /api/auth} önekinden geri alınan yollar.
     *
     * <p>Personel kimlik uçları kiracı bilmeden çalışmalı, bu yüzden {@code /api/auth}
     * bypass listesinde. Ama müşteri portalı kayıt/giriş uçları aynı önek altında
     * duruyor ve ilk satırlarında {@code TenantContext.requireSalonId()} çağırıyor:
     * bypass yüzünden bağlam hiç kurulmuyor ve akış {@code IllegalStateException}
     * ile düşüyordu. Müşteri {@code /b/{slug}} üzerinden geldiği için bağlam
     * oturumdaki public salon seçiminden çözülebiliyor.
     */
    private static final List<String> BYPASS_EXCEPTIONS = List.of("/api/auth/customer");

    private final SalonRepository salonRepository;
    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (BYPASS_EXCEPTIONS.stream().anyMatch(path::startsWith)) {
            return false;
        }
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

        if (salon == null && explicitSlug == null) {
            salon = adoptDefaultTenantForPlatformAdmin(request);
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

    /** Adreste açıkça belirtilen slug: /{slug}, eski /b/{slug}, parametre veya başlık. */
    private String resolveExplicitSlug(HttpServletRequest request) {
        String pathSlug = PublicBookingPath.extractSlug(request.getRequestURI());
        if (pathSlug != null) {
            return pathSlug;
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

    /**
     * Kiracısı olmayan platform yöneticisine varsayılan bir işletme benimsetir.
     *
     * <p>Bu rol {@code salon_id = NULL} ile seed ediliyor (V34). Takvim, dashboard ve
     * org özeti kiracıya bağlı sayfalar olduğu için hiçbiri çözümlenemiyor, istek de
     * {@code /login}'e yönleniyordu: oturum açık olduğu hâlde kullanıcı kendini giriş
     * ekranında buluyor, çıkış yaptırılmış sanıyordu. Aynı şey oturumdaki işletme
     * sonradan askıya alındığında da oluyordu.
     *
     * <p>Seçim oturuma yazılır; yönetici kenar çubuğundaki şube seçicisiyle
     * dilediği işletmeye geçebilir.
     */
    private Salon adoptDefaultTenantForPlatformAdmin(HttpServletRequest request) {
        if (!isPlatformAdminSession(request)) {
            return null;
        }
        Salon salon = salonRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        if (salon != null) {
            request.getSession().setAttribute(TenantContext.SESSION_AUTH_SALON_ID, salon.getId());
        }
        return salon;
    }

    private boolean isPlatformAdminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(TenantContext.SESSION_PLATFORM_ADMIN));
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
        if (path.startsWith("/b/") || PublicBookingPath.isPublicRootPath(path)) {
            response.sendRedirect("/booking");
            return;
        }
        // Aktif tek bir işletme bile yoksa platform yöneticisini girişe atmak yanlış
        // sinyal verir: oturumu geçerli, yalnızca gösterilecek kiracı yok.
        if (isPlatformAdminSession(request)) {
            response.sendRedirect("/platform/tenants");
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
