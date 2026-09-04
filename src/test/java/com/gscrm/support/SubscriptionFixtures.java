package com.gscrm.support;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;

import java.time.LocalDateTime;

/**
 * Testlerin elle kurduğu kiracılara abonelik satırı ekler.
 *
 * <p>{@code SubscriptionService.isWriteAllowed} artık abonelik satırı olmayan bir
 * organizasyonu salt okunur sayıyor (önceden {@code orElse(true)} ile sessizce
 * yazmaya izin veriliyordu). Kiracıyı repository üzerinden kuran testler
 * provisioning akışını atladığı için abonelik satırını da kendileri eklemeli;
 * aksi hâlde her yazma 402 döner ve test, ürün hatası olmadığı hâlde kırılır.
 */
public final class SubscriptionFixtures {

    private SubscriptionFixtures() {
    }

    /** Organizasyona 30 gün süren bir deneme aboneliği ekler. */
    public static void seedTrial(OrganizationSubscriptionRepository subscriptions,
                                 SubscriptionPlanRepository plans,
                                 Long organizationId) {
        Long planId = plans.findByCodeAndActiveTrue("SOLO")
                .map(SubscriptionPlan::getId)
                .orElseThrow(() -> new IllegalStateException("SOLO planı seed edilmemiş"));
        LocalDateTime now = LocalDateTime.now();
        subscriptions.save(OrganizationSubscription.builder()
                .organizationId(organizationId)
                .planId(planId)
                .status("TRIAL")
                .trialEnd(now.plusDays(30))
                .createdAt(now)
                .build());
    }
}
