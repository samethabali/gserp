package com.gscrm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ShowcaseAccessFilter extends OncePerRequestFilter {

    public static final int MAX_SHOWCASE_APPOINTMENTS = 5;

    private static final Set<String> BLOCKED_PREFIXES = Set.of(
            "/api/users",
            "/api/billing",
            "/api/org",
            "/api/expenses",
            "/api/campaigns",
            "/api/inventory",
            "/api/audit",
            "/api/holidays",
            "/users",
            "/settings",
            "/audit",
            "/campaigns",
            "/expenses",
            "/products",
            "/org",
            "/platform"
    );

    private final AppointmentRepository appointmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!TenantContext.isShowcase()) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user
                && user.getRole() == UserRole.PLATFORM_ADMIN) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/customers/") && (path.endsWith("/export") || path.contains("/gdpr"))) {
            forbid(response, "Tanıtım sürümünde veri dışa aktarma kapalıdır");
            return;
        }

        for (String prefix : BLOCKED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?")) {
                forbid(response, "Bu özellik tanıtım sürümünde kapalıdır");
                return;
            }
        }

        if ("PUT".equalsIgnoreCase(method) && path.equals("/api/settings")) {
            forbid(response, "Tanıtım sürümünde ayarlar değiştirilemez");
            return;
        }

        boolean creatingAppointment = "POST".equalsIgnoreCase(method)
                && (path.equals("/api/appointments") || path.equals("/api/appointments/")
                || path.equals("/api/booking/request"));
        if (creatingAppointment) {
            Long salonId = TenantContext.getSalonId();
            if (salonId != null && appointmentRepository.countBySalonId(salonId) >= MAX_SHOWCASE_APPOINTMENTS) {
                forbid(response, "Tanıtım sürümünde en fazla birkaç randevu oluşturulabilir");
                return;
            }
        }

        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)
                && path.startsWith("/api/")
                && !creatingAppointment
                && !path.startsWith("/api/auth/")
                && !path.equals("/api/booking/request")
                && !path.startsWith("/api/onboarding/")) {
            if (path.startsWith("/api/customers") && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                filterChain.doFilter(request, response);
                return;
            }
            forbid(response, "Tanıtım sürümünde bu işlem kapalıdır");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void forbid(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
    }
}
