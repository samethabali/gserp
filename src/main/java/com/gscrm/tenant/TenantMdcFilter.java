package com.gscrm.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantMdcFilter extends OncePerRequestFilter {

    public static final String MDC_SALON_ID = "salonId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long salonId = TenantContext.getSalonId();
        if (salonId != null) {
            MDC.put(MDC_SALON_ID, String.valueOf(salonId));
        }
        Long orgId = TenantContext.getOrgId();
        if (orgId != null) {
            MDC.put("orgId", String.valueOf(orgId));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_SALON_ID);
            MDC.remove("orgId");
        }
    }
}
