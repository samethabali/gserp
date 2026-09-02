package com.gscrm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.enums.UserRole;
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
import java.util.Objects;

/**
 * Kimlik doğrulanmış kullanıcının, isteğin çözümlenen tenant'ı (TenantContext)
 * ile aynı salon/organizasyona ait olduğunu doğrular.
 *
 * Neden gerekli: Tenant, tamamen istemci kontrolündeki {@code X-Salon-Slug}
 * header'ı / cookie'sinden çözümlenir. Bu doğrulama olmadan, A salonuna ait
 * geçerli bir oturumu olan kullanıcı, header'ı B salonuna çevirerek B'nin
 * verisini okuyabilir/yazabilir (yatay yetki yükseltme / cross-tenant sızıntı).
 *
 * Kurallar:
 *  - PLATFORM_ADMIN: tüm tenant'lara erişebilir (bypass).
 *  - ORG_OWNER: kendi organizasyonuna bağlı herhangi bir şubeye erişebilir
 *    (TenantContext.orgId, kullanıcının organizationId'si ile eşleşmeli).
 *  - Diğer roller (BRANCH_MANAGER, RECEPTIONIST, SPECIALIST, CUSTOMER):
 *    yalnızca kendi salonId'lerine eşit tenant bağlamında çalışabilir.
 *
 * Bu filtre JwtAuthenticationFilter'dan SONRA çalışmalıdır (kimlik yerleşmiş olmalı).
 */
@Component
@RequiredArgsConstructor
public class TenantAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Platform uçları tenant bağlamı olmadan çalışır; TenantFilter zaten bypass ediyor.
        return uri.startsWith("/api/platform")
                || uri.startsWith("/actuator")
                || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            if (!isTenantAccessAllowed(user)) {
                writeForbidden(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTenantAccessAllowed(AuthenticatedUser user) {
        if (user.getRole() == UserRole.PLATFORM_ADMIN || TenantContext.isPlatformBypass()) {
            return true;
        }

        Long tenantSalonId = TenantContext.getSalonId();
        // Tenant bağlamı yoksa (örn. tenant bypass edilmiş uç) engelleme.
        if (tenantSalonId == null) {
            return true;
        }

        if (user.getRole() == UserRole.ORG_OWNER) {
            // Org sahibi kendi organizasyonunun herhangi bir şubesine erişebilir.
            Long tenantOrgId = TenantContext.getOrgId();
            return user.getOrganizationId() != null
                    && Objects.equals(user.getOrganizationId(), tenantOrgId);
        }

        // Diğer tüm roller: kullanıcının bağlı olduğu salon = istek tenant'ı olmalı.
        return Objects.equals(user.getSalonId(), tenantSalonId);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("Bu tenant bağlamına erişim yetkiniz yok.")));
    }
}
