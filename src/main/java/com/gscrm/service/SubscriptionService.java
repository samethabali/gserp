package com.gscrm.service;

import com.gscrm.model.*;
import com.gscrm.repository.*;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    public static final String METRIC_WHATSAPP = "whatsapp_sent";
    public static final String METRIC_USERS = "active_users";

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UsageMeterRepository usageMeterRepository;
    private final BillingEventRepository billingEventRepository;

    public Map<String, Object> getCurrentPlan(Long organizationId) {
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Abonelik bulunamadı"));
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan bulunamadı"));

        Map<String, Object> result = new HashMap<>();
        result.put("organizationId", organizationId);
        result.put("status", sub.getStatus());
        result.put("planCode", plan.getCode());
        result.put("planName", plan.getName());
        result.put("maxSalons", plan.getMaxSalons());
        result.put("maxUsers", plan.getMaxUsers());
        result.put("whatsappQuota", plan.getWhatsappQuota());
        result.put("priceMonthly", plan.getPriceMonthly());
        result.put("trialEnd", sub.getTrialEnd());
        result.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
        return result;
    }

    public Map<String, Object> getUsage(Long organizationId) {
        String period = currentPeriod();
        List<UsageMeter> meters = usageMeterRepository.findAll().stream()
                .filter(m -> organizationId.equals(m.getOrganizationId()) && period.equals(m.getPeriod()))
                .toList();

        int whatsappUsed = meters.stream()
                .filter(m -> METRIC_WHATSAPP.equals(m.getMetric()))
                .mapToInt(UsageMeter::getCount)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("whatsappSent", whatsappUsed);
        result.put("metrics", meters);
        return result;
    }

    @Transactional
    public void incrementUsage(Long organizationId, Long salonId, String metric, int delta) {
        String period = currentPeriod();
        UsageMeter meter = usageMeterRepository
                .findByOrganizationIdAndSalonIdAndMetricAndPeriod(organizationId, salonId, metric, period)
                .orElse(UsageMeter.builder()
                        .organizationId(organizationId)
                        .salonId(salonId)
                        .metric(metric)
                        .period(period)
                        .count(0)
                        .build());
        meter.setCount(meter.getCount() + delta);
        usageMeterRepository.save(meter);
    }

    @Transactional
    public void recordBillingEvent(Long organizationId, String eventType, String payload) {
        billingEventRepository.save(BillingEvent.builder()
                .organizationId(organizationId)
                .eventType(eventType)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void handleIyzicoWebhook(String payload) {
        Long orgId = TenantContext.getOrgId();
        if (orgId == null) {
            orgId = 1L;
        }
        recordBillingEvent(orgId, "IYZICO_WEBHOOK", payload);
    }

    public String currentPeriod() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public boolean isWriteAllowed(Long organizationId) {
        if (organizationId == null) {
            return true;
        }
        return subscriptionRepository.findByOrganizationId(organizationId)
                .map(this::isSubscriptionWritable)
                .orElse(true);
    }

    public Map<String, Object> getSubscriptionStatus(Long organizationId) {
        Map<String, Object> result = new HashMap<>();
        if (organizationId == null) {
            result.put("readOnly", false);
            result.put("status", "UNKNOWN");
            return result;
        }

        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElse(null);
        if (sub == null) {
            result.put("readOnly", false);
            result.put("status", "NONE");
            return result;
        }

        boolean writable = isSubscriptionWritable(sub);
        result.put("readOnly", !writable);
        result.put("status", sub.getStatus());
        result.put("trialEnd", sub.getTrialEnd());
        result.put("currentPeriodEnd", sub.getCurrentPeriodEnd());

        if ("TRIAL".equals(sub.getStatus()) && sub.getTrialEnd() != null) {
            long days = ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getTrialEnd());
            result.put("trialDaysRemaining", Math.max(0, days));
        }

        planRepository.findById(sub.getPlanId()).ifPresent(plan -> {
            result.put("planCode", plan.getCode());
            result.put("planName", plan.getName());
        });

        return result;
    }

    private boolean isSubscriptionWritable(OrganizationSubscription sub) {
        String status = sub.getStatus();
        if ("ACTIVE".equals(status)) {
            return true;
        }
        if ("TRIAL".equals(status)) {
            LocalDateTime trialEnd = sub.getTrialEnd();
            return trialEnd == null || LocalDateTime.now().isBefore(trialEnd);
        }
        return false;
    }
}
