package com.gserp.controller;

import com.gserp.dto.request.TenantProvisionRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.TenantProvisionResponse;
import com.gserp.model.OnboardingState;
import com.gserp.repository.OnboardingStateRepository;
import com.gserp.service.SalonProvisioningService;
import com.gserp.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class SalonOnboardingController {

    private final SalonProvisioningService provisioningService;
    private final OnboardingStateRepository onboardingStateRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantProvisionResponse>> register(
            @Valid @RequestBody TenantProvisionRequest request) {
        TenantProvisionResponse result = provisioningService.provision(request);
        return ResponseEntity.ok(ApiResponse.ok("Kayıt tamamlandı", result));
    }

    @GetMapping("/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSteps() {
        Long salonId = TenantContext.requireSalonId();
        OnboardingState state = onboardingStateRepository.findBySalonId(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding kaydı yok"));
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "salonId", salonId,
                "currentStep", state.getCurrentStep(),
                "completedAt", state.getCompletedAt())));
    }

    @PutMapping("/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStep(@RequestBody Map<String, String> body) {
        Long salonId = TenantContext.requireSalonId();
        OnboardingState state = onboardingStateRepository.findBySalonId(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding kaydı yok"));
        String step = body.get("currentStep");
        if (step != null && !step.isBlank()) {
            state.setCurrentStep(step);
        }
        if ("COMPLETED".equals(step)) {
            state.setCompletedAt(LocalDateTime.now());
        }
        state.setUpdatedAt(LocalDateTime.now());
        onboardingStateRepository.save(state);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "currentStep", state.getCurrentStep(),
                "completedAt", state.getCompletedAt())));
    }
}
