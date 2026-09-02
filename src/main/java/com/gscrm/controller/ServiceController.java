package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.service.ServiceDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceDefinitionService serviceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(serviceService.getAll()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(serviceService.getActive()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceDefinition>> getById(@PathVariable Long id) {
        return serviceService.getById(id)
                .map(s -> ResponseEntity.ok(ApiResponse.ok(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceDefinition>> create(@RequestBody ServiceDefinition sd) {
        return ResponseEntity.ok(ApiResponse.ok("Hizmet eklendi", serviceService.create(sd)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceDefinition>> update(@PathVariable Long id, @RequestBody ServiceDefinition sd) {
        return ResponseEntity.ok(ApiResponse.ok("Hizmet güncellendi", serviceService.update(id, sd)));
    }
}
