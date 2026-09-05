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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class SalonOnboardingController {

    /**
     * Kurulum adımları — tek kaynak.
     *
     * <p>Uç, gövdeden gelen adımı doğrulamadan yazıyordu: istemci doğrudan
     * {@code COMPLETED} göndererek sihirbazı atlayabiliyor, yazım hatası da
     * sessizce kalıcı hâle geliyordu. {@code onboarding/setup.html} içindeki
     * {@code STEPS} dizisi bu anahtarların aynısını taşımalı (orada ek olarak
     * başlık ve simge var).
     */
    private static final List<String> STEPS = List.of("SALON_INFO", "SERVICES", "STAFF", "COMPLETED");

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
        return ResponseEntity.ok(ApiResponse.ok(stepPayload(salonId, state)));
    }

    // Kurulum salonun yapılandırmasını değiştiriyor; herhangi bir kimlikli kullanıcı
    // (ör. SPECIALIST) adımı COMPLETED yapabiliyordu. Sayfanın kendisi zaten MGMT istiyor.
    @PutMapping("/steps")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStep(@RequestBody Map<String, String> body) {
        Long salonId = TenantContext.requireSalonId();
        OnboardingState state = requireOrCreateState(salonId);
        String step = body.get("currentStep");
        if (step != null && !step.isBlank()) {
            if (!STEPS.contains(step)) {
                throw new IllegalArgumentException("Geçersiz kurulum adımı: " + step);
            }
            state.setCurrentStep(step);
        }
        if ("COMPLETED".equals(step)) {
            state.setCompletedAt(LocalDateTime.now());
        }
        state.setUpdatedAt(LocalDateTime.now());
        onboardingStateRepository.save(state);
        return ResponseEntity.ok(ApiResponse.ok(stepPayload(salonId, state)));
    }

    /**
     * Yanıt gövdesi.
     *
     * <p>{@code Map.of} null değer kabul etmiyor: kurulum tamamlanmadan
     * {@code completedAt} null olduğu için bu uç <b>her yeni kiracıda</b>
     * NullPointerException ile 500 dönüyordu — yani sihirbaz tam da ihtiyaç
     * duyulduğu anda açılmıyordu. {@code HashMap} null değere izin verir.
     */
    private Map<String, Object> stepPayload(Long salonId, OnboardingState state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("salonId", salonId);
        payload.put("currentStep", state.getCurrentStep());
        payload.put("completedAt", state.getCompletedAt());
        payload.put("steps", STEPS);
        return payload;
    }

    private OnboardingState requireOrCreateState(Long salonId) {
        return onboardingStateRepository.findBySalonId(salonId)
                .orElseGet(() -> onboardingStateRepository.save(OnboardingState.builder()
                        .salonId(salonId)
                        // Kayıp satır kurulumu "bitmiş" göstermemeli: sihirbaz baştan başlasın.
                        .currentStep("SALON_INFO")
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }
}
