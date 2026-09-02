package com.gscrm.controller;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.service.SalonProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final SalonRepository salonRepository;
    private final OrganizationRepository organizationRepository;
    private final SalonProvisioningService provisioningService;

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTenants() {
        List<Map<String, Object>> tenants = salonRepository.findAll().stream().map(salon -> {
            Map<String, Object> row = new HashMap<>();
            row.put("salonId", salon.getId());
            row.put("slug", salon.getSlug());
            row.put("name", salon.getName());
            row.put("organizationId", salon.getOrganizationId());
            row.put("active", salon.isActive());
            organizationRepository.findById(salon.getOrganizationId())
                    .map(Organization::getName)
                    .ifPresent(n -> row.put("organizationName", n));
            return row;
        }).toList();
        return ResponseEntity.ok(ApiResponse.ok(tenants));
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantProvisionResponse>> provision(
            @Valid @RequestBody TenantProvisionRequest request) {
        TenantProvisionResponse result = provisioningService.provision(request);
        return ResponseEntity.ok(ApiResponse.ok("Tenant oluşturuldu", result));
    }
}
