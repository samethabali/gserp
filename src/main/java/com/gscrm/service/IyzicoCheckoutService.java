package com.gscrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.config.IyzicoProperties;
import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IyzicoCheckoutService {

    private final IyzicoProperties iyzicoProperties;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> initiateCheckout(Long organizationId) {
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Abonelik bulunamadı"));
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan bulunamadı"));

        String paymentToken = "gscrm-" + organizationId + "-" + UUID.randomUUID();
        Map<String, Object> result = new HashMap<>();
        result.put("organizationId", organizationId);
        result.put("planCode", plan.getCode());
        result.put("amount", plan.getPriceMonthly());
        result.put("currency", "TRY");
        result.put("paymentToken", paymentToken);

        if (!iyzicoProperties.isEnabled() || iyzicoProperties.isMockMode()) {
            result.put("mock", true);
            result.put("checkoutUrl", "/settings/billing?payToken=" + paymentToken);
            subscriptionService.recordBillingEvent(organizationId, "CHECKOUT_INIT_MOCK",
                    "{\"paymentToken\":\"" + paymentToken + "\"}");
            return result;
        }

        result.put("mock", false);
        result.put("checkoutUrl", iyzicoProperties.getCallbackUrl());
        subscriptionService.recordBillingEvent(organizationId, "CHECKOUT_INIT",
                "{\"paymentToken\":\"" + paymentToken + "\"}");
        log.info("iyzico checkout initiated orgId={} token={}", organizationId, paymentToken);
        return result;
    }

    @Transactional
    public void completeMockCheckout(Long organizationId, String paymentToken) {
        if (paymentToken == null || !paymentToken.startsWith("gscrm-" + organizationId + "-")) {
            throw new IllegalArgumentException("Geçersiz ödeme token");
        }
        subscriptionService.activateSubscription(organizationId, paymentToken);
    }

    @Transactional
    public void handleWebhookPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String paymentId = textOr(root, "paymentId", textOr(root, "paymentConversationId", UUID.randomUUID().toString()));
            Long orgId = parseOrgId(root);
            if (orgId == null) {
                log.warn("iyzico webhook: organizationId çözülemedi");
                return;
            }
            String idempotencyKey = "IYZICO_PAYMENT_" + paymentId;
            if (subscriptionService.hasBillingEvent(orgId, idempotencyKey)) {
                log.info("iyzico webhook duplicate ignored paymentId={}", paymentId);
                return;
            }
            subscriptionService.recordBillingEvent(orgId, idempotencyKey, payload);

            String status = textOr(root, "status", textOr(root, "paymentStatus", ""));
            String eventType = textOr(root, "iyziEventType", "");
            if ("SUCCESS".equalsIgnoreCase(status)
                    || "success".equalsIgnoreCase(status)
                    || eventType.contains("SUCCESS")
                    || root.path("success").asBoolean(false)) {
                subscriptionService.activateSubscription(orgId, paymentId);
            }
        } catch (Exception e) {
            log.error("iyzico webhook parse error: {}", e.getMessage());
            subscriptionService.handleIyzicoWebhook(payload);
        }
    }

    private Long parseOrgId(JsonNode root) {
        if (root.hasNonNull("organizationId")) {
            return root.get("organizationId").asLong();
        }
        String conversation = textOr(root, "paymentConversationId", textOr(root, "conversationId", ""));
        if (conversation.startsWith("gscrm-")) {
            String[] parts = conversation.split("-");
            if (parts.length >= 2) {
                try {
                    return Long.parseLong(parts[1]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText(fallback) : fallback;
    }
}
