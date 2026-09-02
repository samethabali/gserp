package com.gscrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basit in-memory rate limit — yalnızca public booking API için.
 * Prod'da nginx limit_req ikinci katman olarak kullanılmalı.
 */
@Component
public class BookingRateLimitFilter extends OncePerRequestFilter {

    private static final int POST_LIMIT_PER_MINUTE = 10;
    private static final int GET_LIMIT_PER_MINUTE = 60;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/booking");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        boolean isWrite = "POST".equalsIgnoreCase(request.getMethod());
        int limit = isWrite ? POST_LIMIT_PER_MINUTE : GET_LIMIT_PER_MINUTE;

        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.epochMs > 60_000) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });

        if (window.counter.incrementAndGet() > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Çok fazla istek. Lütfen biraz bekleyin.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String salon = com.gscrm.tenant.TenantContext.getSlug();
        String salonPart = salon != null ? salon : "unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return salonPart + ":" + forwarded.split(",")[0].trim();
        }
        return salonPart + ":" + request.getRemoteAddr();
    }

    private record Window(long epochMs, AtomicInteger counter) {}
}
