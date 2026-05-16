package com.gserp.service;

import com.gserp.model.ServiceDefinition;
import com.gserp.repository.ServiceDefinitionRepository;
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

    public List<ServiceDefinition> getAll() {
        return serviceRepository.findAll();
    }

    public List<ServiceDefinition> getActive() {
        return serviceRepository.findByActiveTrue();
    }

    public Optional<ServiceDefinition> getById(Long id) {
        return serviceRepository.findById(id);
    }

    @Transactional
    public ServiceDefinition create(ServiceDefinition sd) {
        sd.setCreatedAt(LocalDateTime.now());
        if (sd.getRequiredResourceIds() == null) {
            sd.setRequiredResourceIds(new ArrayList<>());
        }
        return serviceRepository.save(sd);
    }

    @Transactional
    public ServiceDefinition update(Long id, ServiceDefinition updated) {
        ServiceDefinition existing = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + id));
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
        return serviceRepository.save(existing);
    }
}
