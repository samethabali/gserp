package com.gserp.service;

import com.gserp.model.ServiceDefinition;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServiceDefinitionService {

    private final MockDataStore store;

    public List<ServiceDefinition> getAll() {
        return store.findAllServices();
    }

    public List<ServiceDefinition> getActive() {
        return store.findActiveServices();
    }

    public Optional<ServiceDefinition> getById(Long id) {
        return store.findServiceById(id);
    }

    public ServiceDefinition create(ServiceDefinition sd) {
        return store.addService(sd);
    }

    public ServiceDefinition update(Long id, ServiceDefinition updated) {
        ServiceDefinition existing = store.findServiceById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + id));
        existing.setName(updated.getName());
        existing.setDurationMinutes(updated.getDurationMinutes());
        existing.setBasePrice(updated.getBasePrice());
        existing.setCategory(updated.getCategory());
        existing.setRequiresResource(updated.isRequiresResource());
        existing.setActive(updated.isActive());
        if (updated.getRequiredResourceIds() != null) {
            existing.setRequiredResourceIds(updated.getRequiredResourceIds());
        }
        return store.updateService(existing);
    }
}
