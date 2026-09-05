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

    /**
     * Kota durumunu istisna fırlatmadan bildirir: engel varsa açıklaması, yoksa {@code null}.
     *
     * <p>Personel eklenirken hesabı da açılmaya çalışılır. Kota kontrolü istisna
     * fırlatsaydı, bu servis de işlemsel olduğu için istisna dışarıdaki işlemi
     * rollback-only işaretler ve kota dolduğunda personelin kendisi bile
     * kaydedilemezdi. Çağıran taraf sonuca göre hesabı atlar, personeli yine yazar.
     */
    public String userSeatBlocker(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId).orElse(null);
        if (sub == null) {
            return "Abonelik bulunamadı";
        }
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
        if (plan == null) {
            return "Plan bulunamadı";
        }
        long current = userRepository.countSeatUsersByOrganization(
                organizationId, List.of(UserRole.CUSTOMER, UserRole.PLATFORM_ADMIN));
        return current >= plan.getMaxUsers()
                ? "Kullanıcı kotası dolu (max " + plan.getMaxUsers() + ")"
                : null;
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
