package com.gscrm.controller;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.ImpersonationService;
import com.gscrm.security.StaffScopeService;
import com.gscrm.service.InviteCodeService;
import com.gscrm.service.SalonProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final ImpersonationService impersonationService;
    private final StaffScopeService staffScopeService;
    private final InviteCodeService inviteCodeService;

    @PatchMapping("/tenants/{salonId}/suspend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspend(@PathVariable Long salonId,
                                                                   @RequestBody Map<String, Boolean> body) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Salon bulunamadı"));
        boolean active = body.getOrDefault("active", false);
        salon.setActive(active);
        salonRepository.save(salon);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "salonId", salon.getId(),
                "slug", salon.getSlug(),
                "active", salon.isActive())));
    }

    @PostMapping("/impersonate/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> impersonate(
            @PathVariable Long userId,
            HttpServletRequest request) {
        AuthenticatedUser admin = staffScopeService.requireAuthenticatedUser();
        String redirect = impersonationService.startImpersonation(admin, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("redirectUrl", redirect)));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTenants() {
        List<Map<String, Object>> tenants = salonRepository.findAll().stream().map(salon -> {
            Map<String, Object> row = new HashMap<>();
            row.put("salonId", salon.getId());
            row.put("slug", salon.getSlug());
            row.put("name", salon.getName());
            row.put("organizationId", salon.getOrganizationId());
            row.put("active", salon.isActive());
            row.put("showcase", salon.isShowcase());
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

    @GetMapping("/invites")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listInvites() {
        List<Map<String, Object>> rows = inviteCodeService.list().stream()
                .map(inviteCodeService::toMap)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping("/invites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createInvite(@RequestBody Map<String, String> body) {
        AuthenticatedUser admin = staffScopeService.requireAuthenticatedUser();
        InviteKind kind = InviteKind.PILOT;
        if (body.get("kind") != null && !body.get("kind").isBlank()) {
            kind = InviteKind.valueOf(body.get("kind").trim().toUpperCase());
        }
        Integer maxUses = null;
        if (body.get("maxUses") != null && !body.get("maxUses").isBlank()) {
            maxUses = Integer.parseInt(body.get("maxUses"));
        }
        LocalDateTime expiresAt = null;
        if (body.get("expiresAt") != null && !body.get("expiresAt").isBlank()) {
            expiresAt = LocalDateTime.parse(body.get("expiresAt"));
        }
        InviteCode created = inviteCodeService.create(
                kind,
                maxUses,
                expiresAt,
                body.get("note"),
                body.get("planCode"),
                null,
                admin.getId());
        return ResponseEntity.ok(ApiResponse.ok("Davet kodu oluşturuldu", inviteCodeService.toMap(created)));
    }

    @PostMapping("/invites/{id}/revoke")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeInvite(@PathVariable Long id) {
        InviteCode revoked = inviteCodeService.revoke(id);
        return ResponseEntity.ok(ApiResponse.ok("Davet kodu iptal edildi", inviteCodeService.toMap(revoked)));
    }
}
