package com.gscrm.tenant;

import com.gscrm.model.Salon;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationContextService {

    private final SalonRepository salonRepository;

    public Long resolveOrganizationId(AuthenticatedUser user) {
        if (user != null && user.getOrganizationId() != null) {
            return user.getOrganizationId();
        }
        if (TenantContext.getOrgId() != null) {
            return TenantContext.getOrgId();
        }
        Long salonId = TenantContext.getSalonId();
        if (salonId == null && user != null) {
            salonId = user.getSalonId();
        }
        if (salonId != null) {
            return salonRepository.findById(salonId)
                    .map(Salon::getOrganizationId)
                    .orElse(null);
        }
        return null;
    }
}
