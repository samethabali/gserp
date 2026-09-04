package com.gscrm.controller;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OnboardingStateRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.support.SubscriptionFixtures;
import com.gscrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kurulum sihirbazı adım ucunun doğrulaması ve yetkisi.
 *
 * <p>Uç gövdedeki {@code currentStep} değerini hiç doğrulamadan yazıyordu ve
 * {@code isAuthenticated()} ile korunuyordu: herhangi bir kimlikli kullanıcı —
 * SPECIALIST dahil — kurulumu {@code COMPLETED} işaretleyip sihirbazı atlayabiliyordu.
 * Kayıp durum satırı da {@code COMPLETED} olarak yeniden yaratılıyor, yani silinmiş
 * bir satır kurulumu "bitmiş" gösteriyordu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Kurulum adımları")
class OnboardingStepsIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private OnboardingStateRepository onboardingStateRepository;
    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;

    private final String slug = "setup-" + UUID.randomUUID().toString().substring(0, 8);
    private Long salonId;
    private Long orgId;

    @BeforeEach
    void seedTenant() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Kurulum Org").type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build());
        orgId = org.getId();
        salonId = salonRepository.save(Salon.builder()
                .organizationId(orgId).slug(slug).name("Kurulum Salonu")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
        // Abonelik satırı olmayan kiracının her yazması 402 döner.
        SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, orgId);
    }

    @Test
    @DisplayName("bilinmeyen adım reddedilir")
    void unknownStepIsRejected() throws Exception {
        mockMvc.perform(putStep(UserRole.BRANCH_MANAGER, "{\"currentStep\":\"HACKED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("geçerli adım kaydedilir")
    void knownStepIsAccepted() throws Exception {
        mockMvc.perform(putStep(UserRole.BRANCH_MANAGER, "{\"currentStep\":\"SERVICES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStep").value("SERVICES"));
    }

    @Test
    @DisplayName("COMPLETED tamamlanma zamanını damgalar")
    void completedStampsCompletionTime() throws Exception {
        mockMvc.perform(putStep(UserRole.BRANCH_MANAGER, "{\"currentStep\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("uzman kurulumu tamamlanmış işaretleyemez")
    void specialistCannotCompleteSetup() throws Exception {
        mockMvc.perform(putStep(UserRole.SPECIALIST, "{\"currentStep\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }

    /** Satır yoksa kurulum baştan başlamalı; "bitmiş" varsaymak sihirbazı atlatıyordu. */
    @Test
    @DisplayName("durum satırı yoksa kurulum ilk adımdan başlar")
    void missingStateStartsFromFirstStep() throws Exception {
        onboardingStateRepository.findBySalonId(salonId)
                .ifPresent(state -> onboardingStateRepository.deleteById(state.getId()));

        mockMvc.perform(get("/api/onboarding/steps")
                        .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                        .header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStep").value("SALON_INFO"))
                .andExpect(jsonPath("$.data.steps[0]").value("SALON_INFO"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putStep(
            UserRole role, String body) {
        return put("/api/onboarding/steps")
                .with(authentication(authFor(role))).with(csrf())
                .header("X-Salon-Slug", slug)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(
                8800L, "kurulum-" + role.name().toLowerCase(), "", true, role,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
