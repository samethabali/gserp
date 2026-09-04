package com.gscrm.controller;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.OnboardingState;
import com.gscrm.repository.OnboardingStateRepository;
import com.gscrm.service.InviteCodeService;
import com.gscrm.security.ClientIpResolver;
import com.gscrm.service.SalonProvisioningService;
import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class SalonOnboardingController {

    private final SalonProvisioningService provisioningService;
    private final InviteCodeService inviteCodeService;
    private final OnboardingStateRepository onboardingStateRepository;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantProvisionResponse>> register(
            @Valid @RequestBody TenantProvisionRequest request,
            HttpServletRequest httpRequest) {
        TenantProvisionResponse result =
                inviteCodeService.registerWithInvite(request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Kayıt tamamlandı", result));
    }

    @GetMapping("/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSteps() {
        Long salonId = TenantContext.requireSalonId();
        OnboardingState state = requireOrCreateState(salonId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "salonId", salonId,
                "currentStep", state.getCurrentStep(),
                "completedAt", state.getCompletedAt(),
                "steps", List.of("SALON_INFO", "SERVICES", "STAFF", "COMPLETED"))));
    }

    @PutMapping("/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStep(@RequestBody Map<String, String> body) {
        Long salonId = TenantContext.requireSalonId();
        OnboardingState state = requireOrCreateState(salonId);
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

    private OnboardingState requireOrCreateState(Long salonId) {
        return onboardingStateRepository.findBySalonId(salonId)
                .orElseGet(() -> onboardingStateRepository.save(OnboardingState.builder()
                        .salonId(salonId)
                        .currentStep("COMPLETED")
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }
}
