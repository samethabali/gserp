package com.gscrm.service;

import com.gscrm.model.BranchServicePrice;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.repository.BranchServicePriceRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchPricingService {

    private final BranchServicePriceRepository branchServicePriceRepository;
    private final ServiceDefinitionRepository serviceDefinitionRepository;

    public BigDecimal effectivePrice(Long serviceId) {
        Long salonId = TenantContext.requireSalonId();
        return branchServicePriceRepository.findBySalonIdAndServiceIdAndActiveTrue(salonId, serviceId)
                .map(BranchServicePrice::getPriceOverride)
                .orElseGet(() -> serviceDefinitionRepository.findByIdAndSalonId(serviceId, salonId)
                        .map(ServiceDefinition::getBasePrice)
                        .orElse(BigDecimal.ZERO));
    }

    public int effectiveDuration(Long serviceId) {
        Long salonId = TenantContext.requireSalonId();
        Optional<Integer> override = branchServicePriceRepository.findBySalonIdAndServiceIdAndActiveTrue(salonId, serviceId)
                .map(BranchServicePrice::getDurationOverride)
                .filter(d -> d != null && d > 0);
        if (override.isPresent()) {
            return override.get();
        }
        return serviceDefinitionRepository.findByIdAndSalonId(serviceId, salonId)
                .map(ServiceDefinition::getDurationMinutes)
                .orElse(30);
    }

    @Transactional
    public BranchServicePrice upsert(Long serviceId, BigDecimal priceOverride, Integer durationOverride) {
        Long salonId = TenantContext.requireSalonId();
        BranchServicePrice row = branchServicePriceRepository.findBySalonIdAndServiceIdAndActiveTrue(salonId, serviceId)
                .orElse(BranchServicePrice.builder().salonId(salonId).serviceId(serviceId).active(true).build());
        if (priceOverride != null) row.setPriceOverride(priceOverride);
        if (durationOverride != null) row.setDurationOverride(durationOverride);
        return branchServicePriceRepository.save(row);
    }
}
