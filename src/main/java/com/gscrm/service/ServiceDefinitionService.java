package com.gscrm.service;

import com.gscrm.model.ServiceDefinition;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.FieldDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceDefinitionService {

    private final ServiceDefinitionRepository serviceRepository;
    private final ActivityEventService activityEventService;

    public List<ServiceDefinition> getAll() {
        return serviceRepository.findAll();
    }

    public List<ServiceDefinition> getActive() {
        return serviceRepository.findBySalonIdAndActiveTrue(TenantContext.requireSalonId());
    }

    public Optional<ServiceDefinition> getById(Long id) {
        return serviceRepository.findById(id);
    }

    @Transactional
    public ServiceDefinition create(ServiceDefinition sd) {
        // Uçlar ham entity kabul ettiği için istemci gövdeye salonId koyabilir;
        // tenant sunucu tarafında zorlanır (mass assignment koruması).
        sd.setSalonId(TenantContext.requireSalonId());
        sd.setCreatedAt(LocalDateTime.now());
        if (sd.getRequiredResourceIds() == null) {
            sd.setRequiredResourceIds(new ArrayList<>());
        }
        ServiceDefinition saved = serviceRepository.save(sd);
        activityEventService.record("CREATE", "SERVICE", saved.getId(), null,
                "Hizmet eklendi: " + saved.getName());
        return saved;
    }

    @Transactional
    public ServiceDefinition update(Long id, ServiceDefinition updated) {
        ServiceDefinition existing = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + id));

        // Setter'lardan önce: fiyat ve süre değişimi kütükte görünsün.
        String prevName = existing.getName();
        int prevDuration = existing.getDurationMinutes();
        var prevPrice = existing.getBasePrice();
        var prevCategory = existing.getCategory();
        boolean prevActive = existing.isActive();

        existing.setName(updated.getName());
        existing.setDurationMinutes(updated.getDurationMinutes());
        existing.setBasePrice(updated.getBasePrice());
        existing.setCategory(updated.getCategory());
        existing.setRequiresResource(updated.isRequiresResource());
        existing.setActive(updated.isActive());
        if (updated.getRequiredResourceIds() != null) {
            existing.getRequiredResourceIds().clear();
            existing.getRequiredResourceIds().addAll(updated.getRequiredResourceIds());
        }
        ServiceDefinition saved = serviceRepository.save(existing);
        activityEventService.recordChange("UPDATE", "SERVICE", saved.getId(), null,
                "Hizmet güncellendi: " + saved.getName(),
                FieldDiff.create()
                        .compare("ad", prevName, saved.getName())
                        .compare("sureDk", prevDuration, saved.getDurationMinutes())
                        .compare("fiyat", prevPrice, saved.getBasePrice())
                        .compare("kategori", prevCategory, saved.getCategory())
                        .compare("aktif", prevActive, saved.isActive())
                        .toJson());
        return saved;
    }
}
