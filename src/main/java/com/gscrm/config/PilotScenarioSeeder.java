package com.gscrm.config;

import com.gscrm.model.*;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.model.enums.DiscountType;
import com.gscrm.repository.*;
import com.gscrm.service.ServiceTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Pilot Senaryo A/B test verisi — dev profilinde idempotent.
 * Bkz. docs/saas/pilot-scenarios.md
 */
@Slf4j
@Component
@Profile("dev")
@Order(11)
@RequiredArgsConstructor
public class PilotScenarioSeeder implements CommandLineRunner {

    private static final Long DEFAULT_SALON_ID = 1L;
    private static final String SCENARIO_A_SLUG = "guzellik-atolyesi";
    private static final String SCENARIO_B_SLUG_KADIKOY = "belleza-kadikoy";
    private static final String SCENARIO_B_SLUG_BESIKTAS = "belleza-besiktas";

    /** Deneme süresi tek yerden gelir; aynı sabit daha önce üç ayrı dosyada tekrarlanıyordu. */
    private final AppProperties appProperties;
    private final SalonRepository salonRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OnboardingStateRepository onboardingStateRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SalonSettingRepository salonSettingRepository;
    private final StaffRepository staffRepository;
    private final ServiceTemplateService serviceTemplateService;
    private final PasswordEncoder passwordEncoder;
    private final CouponRepository couponRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensureDefaultOnboardingCompleted();
        seedScenarioAIfMissing();
        seedScenarioBIfMissing();
    }

    private void ensureDefaultOnboardingCompleted() {
        onboardingStateRepository.findBySalonId(DEFAULT_SALON_ID).ifPresentOrElse(
                state -> {
                    if (!"COMPLETED".equals(state.getCurrentStep())) {
                        state.setCurrentStep("COMPLETED");
                        state.setCompletedAt(LocalDateTime.now());
                        state.setUpdatedAt(LocalDateTime.now());
                        onboardingStateRepository.save(state);
                    }
                },
                () -> onboardingStateRepository.save(OnboardingState.builder()
                        .salonId(DEFAULT_SALON_ID)
                        .currentStep("COMPLETED")
                        .completedAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
        );
    }

    private void seedScenarioAIfMissing() {
        if (salonRepository.findBySlugAndActiveTrue(SCENARIO_A_SLUG).isPresent()) {
            return;
        }
        log.info("Seeding pilot Senaryo A: {}", SCENARIO_A_SLUG);
        LocalDateTime now = LocalDateTime.now();

        Organization org = organizationRepository.save(Organization.builder()
                .name("Güzellik Atölyesi")
                .type(OrganizationType.STANDALONE)
                .active(true)
                .loyaltyPolicy("SALON")
                .createdAt(now)
                .build());

        Salon salon = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug(SCENARIO_A_SLUG)
                .name("Güzellik Atölyesi")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .contactEmail("info@guzellik-atolyesi.test")
                .build());

        seedSalonSettings(salon, now);
        serviceTemplateService.seedHairAndSkinMenu(salon.getId());

        staffRepository.save(Staff.builder()
                .salonId(salon.getId())
                .name("Demo Uzman")
                .phone("05321000099")
                .email("uzman@guzellik-atolyesi.test")
                .role(StaffRole.SPECIALIST)
                .colorHex("#e91e8c")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        userRepository.save(User.builder()
                .salonId(salon.getId())
                .organizationId(org.getId())
                .username("admin@guzellik-atolyesi")
                .passwordHash(passwordEncoder.encode("admin"))
                .role(UserRole.BRANCH_MANAGER)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(now)
                .build());

        onboardingStateRepository.save(OnboardingState.builder()
                .salonId(salon.getId())
                .currentStep("SALON_INFO")
                .updatedAt(now)
                .build());

        SubscriptionPlan solo = subscriptionPlanRepository.findByCodeAndActiveTrue("SOLO")
                .orElseThrow();
        organizationSubscriptionRepository.save(OrganizationSubscription.builder()
                .organizationId(org.getId())
                .planId(solo.getId())
                .status("TRIAL")
                .trialEnd(now.plusDays(appProperties.getDefaultTrialDays()))
                .createdAt(now)
                .build());
    }

    private void seedScenarioBIfMissing() {
        if (salonRepository.findBySlugAndActiveTrue(SCENARIO_B_SLUG_KADIKOY).isPresent()) {
            return;
        }
        log.info("Seeding pilot Senaryo B: Belleza Chain");
        LocalDateTime now = LocalDateTime.now();

        Organization org = organizationRepository.save(Organization.builder()
                .name("Belleza Chain")
                .type(OrganizationType.FRANCHISE)
                .active(true)
                .loyaltyPolicy("ORG")
                .createdAt(now)
                .build());

        Salon kadikoy = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug(SCENARIO_B_SLUG_KADIKOY)
                .name("Belleza Kadıköy")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .contactEmail("kadikoy@belleza.test")
                .build());

        Salon besiktas = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug(SCENARIO_B_SLUG_BESIKTAS)
                .name("Belleza Beşiktaş")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .contactEmail("besiktas@belleza.test")
                .build());

        for (Salon salon : java.util.List.of(kadikoy, besiktas)) {
            seedSalonSettings(salon, now);
            serviceTemplateService.seedHairAndSkinMenu(salon.getId());
            onboardingStateRepository.save(OnboardingState.builder()
                    .salonId(salon.getId())
                    .currentStep("COMPLETED")
                    .completedAt(now)
                    .updatedAt(now)
                    .build());
        }

        String encoded = passwordEncoder.encode("admin");
        userRepository.save(User.builder()
                .salonId(kadikoy.getId())
                .organizationId(org.getId())
                .username("owner@belleza")
                .passwordHash(encoded)
                .role(UserRole.ORG_OWNER)
                .enabled(true)
                .mustChangePassword(false)
                .createdAt(now)
                .build());

        userRepository.save(User.builder()
                .salonId(kadikoy.getId())
                .organizationId(org.getId())
                .username("mgr-kadikoy")
                .passwordHash(encoded)
                .role(UserRole.BRANCH_MANAGER)
                .enabled(true)
                .mustChangePassword(false)
                .createdAt(now)
                .build());

        userRepository.save(User.builder()
                .salonId(besiktas.getId())
                .organizationId(org.getId())
                .username("mgr-besiktas")
                .passwordHash(encoded)
                .role(UserRole.BRANCH_MANAGER)
                .enabled(true)
                .mustChangePassword(false)
                .createdAt(now)
                .build());

        SubscriptionPlan franchise = subscriptionPlanRepository.findByCodeAndActiveTrue("FRANCHISE_STARTER")
                .orElseThrow();
        organizationSubscriptionRepository.save(OrganizationSubscription.builder()
                .organizationId(org.getId())
                .planId(franchise.getId())
                .status("TRIAL")
                .trialEnd(now.plusDays(appProperties.getDefaultTrialDays()))
                .createdAt(now)
                .build());

        if (!couponRepository.existsByCodeIgnoreCase("BELLEZA10")) {
            couponRepository.save(Coupon.builder()
                    .salonId(kadikoy.getId())
                    .organizationId(org.getId())
                    .scope("ORG")
                    .code("BELLEZA10")
                    .description("Franchise %10 indirim")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new java.math.BigDecimal("10"))
                    .active(true)
                    .usedCount(0)
                    .createdAt(now)
                    .build());
        }
    }

    private void seedSalonSettings(Salon salon, LocalDateTime now) {
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
