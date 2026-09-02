package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.StaffScopeService;
import com.gscrm.tenant.OrganizationContextService;
import com.gscrm.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final SubscriptionService subscriptionService;
    private final StaffScopeService staffScopeService;
    private final OrganizationContextService organizationContextService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = requireOrganizationId(user);
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getSubscriptionStatus(orgId)));
    }

    @GetMapping("/plan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> plan() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = requireOrganizationId(user);
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getCurrentPlan(orgId)));
    }

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> usage() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = requireOrganizationId(user);
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getUsage(orgId)));
    }

    private Long requireOrganizationId(AuthenticatedUser user) {
        Long orgId = organizationContextService.resolveOrganizationId(user);
        if (orgId == null) {
            throw new IllegalStateException("Organizasyon bağlamı yok");
        }
        return orgId;
    }
}
