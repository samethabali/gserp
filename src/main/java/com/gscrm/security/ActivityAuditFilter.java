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

@Component
@RequiredArgsConstructor
public class ActivityAuditFilter extends OncePerRequestFilter {

    private static final Pattern CUSTOMER_PATH = Pattern.compile("^/api/customers/(\\d+)");

    private final ActivityEventService activityEventService;

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
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/webhooks/")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (response.getStatus() >= 400 || TenantContext.getSalonId() == null) {
            return;
        }
        String path = request.getRequestURI();
        Long customerId = null;
        Matcher matcher = CUSTOMER_PATH.matcher(path);
        if (matcher.find()) {
            customerId = Long.parseLong(matcher.group(1));
        }
        String ip = request.getRemoteAddr();
        activityEventService.record(
                request.getMethod(),
                "HTTP",
                null,
                customerId,
                request.getMethod() + " " + path,
                null,
                ip);
    }
}
