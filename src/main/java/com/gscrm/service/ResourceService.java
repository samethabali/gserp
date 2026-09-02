package com.gscrm.service;

import com.gscrm.model.Resource;
import com.gscrm.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public List<Resource> getAll() {
        return resourceRepository.findAll();
    }

    public List<Resource> getActive() {
        return resourceRepository.findByActiveTrue();
    }

    public Optional<Resource> getById(Long id) {
        return resourceRepository.findById(id);
    }

    @Transactional
    public Resource create(Resource resource) {
        resource.setCreatedAt(LocalDateTime.now());
        return resourceRepository.save(resource);
    }

    @Transactional
    public Resource update(Long id, Resource updated) {
        Resource existing = resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kaynak bulunamadı: " + id));
        existing.setName(updated.getName());
        existing.setResourceType(updated.getResourceType());
        existing.setCapacity(updated.getCapacity());
        existing.setActive(updated.isActive());
        return resourceRepository.save(existing);
    }
}
