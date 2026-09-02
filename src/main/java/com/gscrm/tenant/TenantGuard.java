package com.gscrm.tenant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TenantGuard {

    public void assertSameSalon(Long entitySalonId) {
        if (TenantContext.isPlatformBypass()) {
            return;
        }
        if (!Objects.equals(TenantContext.requireSalonId(), entitySalonId)) {
            throw new AccessDeniedException("Salon tenant mismatch");
        }
    }

    public void assertSameSalon(TenantEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        assertSameSalon(entity.getSalonId());
    }
}
