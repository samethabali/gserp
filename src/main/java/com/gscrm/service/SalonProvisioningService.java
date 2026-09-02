package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.*;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.*;
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
    private final ServiceTemplateService serviceTemplateService;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;

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
                .showcase(request.isShowcase())
                .createdAt(now)
                .contactEmail(request.getContactEmail())
                .build());

        seedDefaultSettings(salon, now);
        serviceTemplateService.seedHairAndSkinMenu(salon.getId());
        if (request.isShowcase()) {
            seedShowcaseSample(salon.getId(), now);
        }

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

    private void seedShowcaseSample(Long salonId, LocalDateTime now) {
        staffRepository.save(Staff.builder()
                .salonId(salonId)
                .name("Örnek Uzman")
                .role(com.gscrm.model.enums.StaffRole.SPECIALIST)
                .colorHex("#9b59b6")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        customerRepository.save(Customer.builder()
                .salonId(salonId)
                .homeSalonId(salonId)
                .firstName("Ayşe")
                .lastName("Demir")
                .phone("05551112233")
                .createdAt(now)
                .build());
        customerRepository.save(Customer.builder()
                .salonId(salonId)
                .homeSalonId(salonId)
                .firstName("Elif")
                .lastName("Yılmaz")
                .phone("05554445566")
                .createdAt(now)
                .build());
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
