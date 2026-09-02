package com.gscrm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.enums.UserRole;
import com.gscrm.service.SubscriptionService;
import com.gscrm.tenant.OrganizationContextService;
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
public class SubscriptionReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_EXEMPT_PREFIXES = Set.of(
            "/api/auth/",
            "/api/booking/",
            "/api/customer/",
            "/api/webhooks/",
            "/api/onboarding/register"
    );

    private final SubscriptionService subscriptionService;
    private final OrganizationContextService organizationContextService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        for (String prefix : WRITE_EXEMPT_PREFIXES) {
            if (request.getRequestURI().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            if (user.getRole() == UserRole.PLATFORM_ADMIN) {
                filterChain.doFilter(request, response);
                return;
            }
            Long orgId = organizationContextService.resolveOrganizationId(user);
            if (!subscriptionService.isWriteAllowed(orgId)) {
                response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(
                        ApiResponse.error("Deneme süresi doldu. Salt okunur moddasınız; planı yükseltmek için faturalandırma sayfasını ziyaret edin.")));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
