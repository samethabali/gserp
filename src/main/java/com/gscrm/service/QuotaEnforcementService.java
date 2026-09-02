package com.gscrm.service;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuotaEnforcementService {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final SalonRepository salonRepository;

    public void assertCanAddUser(Long organizationId) {
        SubscriptionPlan plan = resolvePlan(organizationId);
        long current = userRepository.countSeatUsersByOrganization(
                organizationId, List.of(UserRole.CUSTOMER, UserRole.PLATFORM_ADMIN));
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

    private SubscriptionPlan resolvePlan(Long organizationId) {
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Abonelik bulunamadı"));
        return planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan bulunamadı"));
    }
}
