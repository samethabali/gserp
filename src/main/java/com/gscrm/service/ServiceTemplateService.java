package com.gscrm.service;

import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.repository.ServiceDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Yeni salonlar için başlangıç hizmet menüsü (pilot spec: Saç + Cilt).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceTemplateService {

    private final ServiceDefinitionRepository serviceDefinitionRepository;

    @Transactional
    public List<ServiceDefinition> seedHairAndSkinMenu(Long salonId) {
        if (serviceDefinitionRepository.existsBySalonId(salonId)) {
            return serviceDefinitionRepository.findBySalonIdAndActiveTrue(salonId);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ServiceDefinition> services = List.of(
                service(salonId, "Saç Kesim", 45, "250", ServiceCategory.HAIR, now),
                service(salonId, "Fön", 30, "150", ServiceCategory.HAIR, now),
                service(salonId, "Saç Boyama", 120, "800", ServiceCategory.HAIR, now),
                service(salonId, "Cilt Bakımı", 60, "500", ServiceCategory.SKIN, now),
                service(salonId, "Yüz Maskesi", 45, "350", ServiceCategory.SKIN, now)
        );
        return serviceDefinitionRepository.saveAll(services);
    }

    private ServiceDefinition service(Long salonId, String name, int durationMinutes,
                                      String price, ServiceCategory category, LocalDateTime now) {
        return ServiceDefinition.builder()
                .salonId(salonId)
                .name(name)
                .durationMinutes(durationMinutes)
                .basePrice(new BigDecimal(price))
                .category(category)
                .requiresResource(false)
                .active(true)
                .createdAt(now)
                .requiredResourceIds(new ArrayList<>())
                .build();
    }
}
