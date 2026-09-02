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
import java.util.stream.Collectors;

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
    private final SalonRepository salonRepository;
    private final UserRepository userRepository;

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
        // Org+dönem'e scope'lu sorgu (tüm tablo taranmaz).
        List<UsageMeter> meters = usageMeterRepository.findByOrganizationIdAndPeriod(organizationId, period);
        // WhatsApp toplamı DB tarafında SUM ile hesaplanır.
        int whatsappUsed = (int) usageMeterRepository.sumCount(organizationId, METRIC_WHATSAPP, period);

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

    public boolean hasBillingEvent(Long organizationId, String eventType) {
        return billingEventRepository.existsByOrganizationIdAndEventType(organizationId, eventType);
    }

    @Transactional
    public void activateSubscription(Long organizationId, String externalId) {
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Abonelik bulunamadı"));
        LocalDateTime now = LocalDateTime.now();
        sub.setStatus("ACTIVE");
        sub.setExternalId(externalId);
        sub.setCurrentPeriodEnd(now.plusMonths(1));
        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);
        recordBillingEvent(organizationId, "SUBSCRIPTION_ACTIVATED", externalId);
    }

    public List<Map<String, Object>> getBillingEvents(Long organizationId) {
        return billingEventRepository.findTop20ByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", e.getId());
                    row.put("eventType", e.getEventType());
                    row.put("createdAt", e.getCreatedAt());
                    row.put("payloadPreview", truncate(e.getPayload(), 120));
                    return row;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getQuotaStatus(Long organizationId) {
        Map<String, Object> result = new HashMap<>();
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId).orElse(null);
        if (sub == null) {
            return result;
        }
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
        if (plan == null) {
            return result;
        }
        int salonCount = salonRepository.findByOrganizationIdAndActiveTrue(organizationId).size();
        long userCount = userRepository.countByOrganizationIdAndEnabledTrue(organizationId);
        Map<String, Object> usage = getUsage(organizationId);
        int whatsappUsed = (Integer) usage.getOrDefault("whatsappSent", 0);

        result.put("salonsUsed", salonCount);
        result.put("salonsMax", plan.getMaxSalons());
        result.put("usersUsed", userCount);
        result.put("usersMax", plan.getMaxUsers());
        result.put("whatsappUsed", whatsappUsed);
        result.put("whatsappMax", plan.getWhatsappQuota());
        result.put("salonQuotaWarning", salonCount >= plan.getMaxSalons());
        result.put("userQuotaWarning", userCount >= plan.getMaxUsers());
        result.put("whatsappQuotaWarning", whatsappUsed >= plan.getWhatsappQuota() * 0.9);
        return result;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
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
