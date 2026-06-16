package com.gserp.tenant;

import com.gserp.model.Salon;
import com.gserp.repository.SalonRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private static final String SALON_SLUG_HEADER = "X-Salon-Slug";

    private final SalonRepository salonRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/api/platform")
                || path.equals("/api/onboarding/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/platform")) {
            TenantContext.setPlatformBypass(true);
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        String slug = resolveSlug(request);
        Salon salon = salonRepository.findBySlugAndActiveTrue(slug).orElse(null);
        if (salon == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Salon bulunamadı: " + slug + "\"}");
            return;
        }

        TenantContext.setSalonId(salon.getId());
        TenantContext.setOrgId(salon.getOrganizationId());
        TenantContext.setSlug(salon.getSlug());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        String headerSlug = request.getHeader(SALON_SLUG_HEADER);
        if (headerSlug != null && !headerSlug.isBlank()) {
            return headerSlug.trim().toLowerCase();
        }

        String host = request.getServerName();
        if (host == null || host.isBlank()) {
            return "default";
        }

        host = host.toLowerCase();
        if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
            return "default";
        }

        if (host.endsWith(".localhost")) {
            return host.substring(0, host.length() - ".localhost".length());
        }

        int dot = host.indexOf('.');
        if (dot > 0) {
            return host.substring(0, dot);
        }

        return "default";
    }
}
