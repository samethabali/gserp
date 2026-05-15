package com.gserp.service;

import com.gserp.model.Resource;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final MockDataStore store;

    public List<Resource> getAll() {
        return store.findAllResources();
    }

    public List<Resource> getActive() {
        return store.findActiveResources();
    }

    public Optional<Resource> getById(Long id) {
        return store.findResourceById(id);
    }

    public Resource create(Resource resource) {
        return store.addResource(resource);
    }

    public Resource update(Long id, Resource updated) {
        Resource existing = store.findResourceById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kaynak bulunamadı: " + id));
        existing.setName(updated.getName());
        existing.setResourceType(updated.getResourceType());
        existing.setCapacity(updated.getCapacity());
        existing.setActive(updated.isActive());
        return store.updateResource(existing);
    }
}
