package com.gserp.service;

import com.gserp.model.OrganizationSubscription;
import com.gserp.model.SubscriptionPlan;
import com.gserp.model.enums.UserRole;
import com.gserp.repository.OrganizationSubscriptionRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.repository.SubscriptionPlanRepository;
import com.gserp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuotaEnforcementService {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final SalonRepository salonRepository;
    private final SubscriptionService subscriptionService;

    public void assertCanAddUser(Long organizationId) {
        SubscriptionPlan plan = resolvePlan(organizationId);
        long current = userRepository.findAll().stream()
                .filter(u -> organizationId.equals(u.getOrganizationId()))
                .filter(u -> u.getRole() != UserRole.CUSTOMER && u.getRole() != UserRole.PLATFORM_ADMIN)
                .count();
        if (current >= plan.getMaxUsers()) {
            throw new IllegalStateException("Kullanıcı kotası doldu (max " + plan.getMaxUsers() + ")");
        }
    }

    public void assertCanAddSalon(Long organizationId) {
        SubscriptionPlan plan = resolvePlan(organizationId);
        long current = salonRepository.findByOrganizationIdAndActiveTrue(organizationId).size();
        if (current >= plan.getMaxSalons()) {
            throw new IllegalStateException("Şube kotası doldu (max " + plan.getMaxSalons() + ")");
        }
    }

    public void assertWhatsAppQuota(Long organizationId) {
        SubscriptionPlan plan = resolvePlan(organizationId);
        var usage = subscriptionService.getUsage(organizationId);
        int used = (int) usage.get("whatsappSent");
        if (used >= plan.getWhatsappQuota()) {
            throw new IllegalStateException("WhatsApp kotası doldu (max " + plan.getWhatsappQuota() + ")");
        }
    }

    private SubscriptionPlan resolvePlan(Long organizationId) {
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Abonelik bulunamadı"));
        return planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan bulunamadı"));
    }
}
