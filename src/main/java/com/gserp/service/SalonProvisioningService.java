package com.gserp.service;

import com.gserp.dto.request.TenantProvisionRequest;
import com.gserp.dto.response.TenantProvisionResponse;
import com.gserp.model.*;
import com.gserp.model.enums.OrganizationType;
import com.gserp.model.enums.UserRole;
import com.gserp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalonProvisioningService {

    private final OrganizationRepository organizationRepository;
    private final SalonRepository salonRepository;
    private final SalonSettingRepository salonSettingRepository;
    private final UserRepository userRepository;
    private final OnboardingStateRepository onboardingStateRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantProvisionResponse provision(TenantProvisionRequest request) {
        String slug = request.getSalonSlug().trim().toLowerCase();
        if (salonRepository.findBySlugAndActiveTrue(slug).isPresent()) {
            throw new IllegalArgumentException("Bu slug zaten kullanılıyor: " + slug);
        }

        LocalDateTime now = LocalDateTime.now();
        OrganizationType orgType = request.getOrganizationType() != null
                ? request.getOrganizationType() : OrganizationType.STANDALONE;

        Organization org = organizationRepository.save(Organization.builder()
                .name(request.getOrganizationName())
                .type(orgType)
                .active(true)
                .createdAt(now)
                .loyaltyPolicy("SALON")
                .build());

        Salon salon = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug(slug)
                .name(request.getSalonName())
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .contactEmail(request.getContactEmail())
                .build());

        seedDefaultSettings(salon, now);

        User admin = userRepository.save(User.builder()
                .salonId(salon.getId())
                .organizationId(org.getId())
                .username(request.getAdminUsername())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .role(UserRole.BRANCH_MANAGER)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(now)
                .build());

        OnboardingState onboarding = onboardingStateRepository.save(OnboardingState.builder()
                .salonId(salon.getId())
                .currentStep("SALON_INFO")
                .updatedAt(now)
                .build());

        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeAndActiveTrue(
                        request.getPlanCode() != null ? request.getPlanCode() : "SOLO")
                .orElseThrow(() -> new IllegalArgumentException("Plan bulunamadı"));

        LocalDateTime trialEnd = now.plusDays(14);
        organizationSubscriptionRepository.save(OrganizationSubscription.builder()
                .organizationId(org.getId())
                .planId(plan.getId())
                .status("TRIAL")
                .trialEnd(trialEnd)
                .createdAt(now)
                .build());

        return TenantProvisionResponse.builder()
                .organizationId(org.getId())
                .salonId(salon.getId())
                .salonSlug(salon.getSlug())
                .adminUserId(admin.getId())
                .onboardingStep(onboarding.getCurrentStep())
                .trialEnd(trialEnd)
                .build();
    }

    private void seedDefaultSettings(Salon salon, LocalDateTime now) {
        saveSetting(salon.getId(), "salon.name", salon.getName(), now);
        saveSetting(salon.getId(), "salon.primary_color", "#e91e8c", now);
        saveSetting(salon.getId(), "salon.logo_url", "", now);
    }

    private void saveSetting(Long salonId, String key, String value, LocalDateTime now) {
        salonSettingRepository.save(SalonSetting.builder()
                .salonId(salonId)
                .key(key)
                .value(value)
                .updatedAt(now)
                .build());
    }
}
