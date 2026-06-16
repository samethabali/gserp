package com.gserp.security;

import com.gserp.model.Appointment;
import com.gserp.model.enums.UserRole;
import com.gserp.repository.SalonRepository;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchScopeService {

    private final SalonRepository salonRepository;

    public void assertCanAccessAppointment(Appointment appointment, AuthenticatedUser user) {
        if (user.getRole() == UserRole.PLATFORM_ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.ORG_OWNER) {
            assertOrgSalon(appointment.getSalonId(), user.getOrganizationId());
            return;
        }
        assertSameSalon(appointment.getSalonId(), user.getSalonId());
        if (user.getRole() == UserRole.SPECIALIST) {
            if (user.getStaffId() == null || !user.getStaffId().equals(appointment.getStaffId())) {
                throw new org.springframework.security.access.AccessDeniedException("Bu randevuya erişim yetkiniz yok");
            }
        }
    }

    public void assertCanAccessSalon(Long salonId, AuthenticatedUser user) {
        if (user.getRole() == UserRole.PLATFORM_ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.ORG_OWNER) {
            assertOrgSalon(salonId, user.getOrganizationId());
            return;
        }
        assertSameSalon(salonId, user.getSalonId());
    }

    public List<Long> accessibleSalonIds(AuthenticatedUser user) {
        if (user.getRole() == UserRole.PLATFORM_ADMIN) {
            return salonRepository.findAll().stream().map(s -> s.getId()).toList();
        }
        if (user.getRole() == UserRole.ORG_OWNER && user.getOrganizationId() != null) {
            return salonRepository.findByOrganizationIdAndActiveTrue(user.getOrganizationId())
                    .stream().map(s -> s.getId()).toList();
        }
        if (user.getSalonId() != null) {
            return List.of(user.getSalonId());
        }
        return List.of();
    }

    public Long resolveEffectiveSalonId(AuthenticatedUser user) {
        if (TenantContext.getSalonId() != null) {
            return TenantContext.getSalonId();
        }
        return user.getSalonId();
    }

    private void assertOrgSalon(Long salonId, Long orgId) {
        if (orgId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Organizasyon bağlamı yok");
        }
        salonRepository.findById(salonId)
                .filter(s -> orgId.equals(s.getOrganizationId()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Bu şubeye erişim yetkiniz yok"));
    }

    private void assertSameSalon(Long entitySalonId, Long userSalonId) {
        if (entitySalonId == null || userSalonId == null || !entitySalonId.equals(userSalonId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bu kaynağa erişim yetkiniz yok");
        }
    }
}
