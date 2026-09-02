package com.gscrm.notification.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.SalonWhatsAppConfig;
import com.gscrm.service.SalonWhatsAppService;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final WhatsAppProperties properties;
    private final SalonWhatsAppService salonWhatsAppService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && matchesVerifyToken(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody(required = false) String payload) {
        if (payload != null && !payload.isBlank()) {
            routeByPhoneNumberId(payload);
            log.debug("WhatsApp webhook payload alındı ({} byte)", payload.length());
        }
        return ResponseEntity.ok().build();
    }

    private void routeByPhoneNumberId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode entries = root.path("entry");
            if (!entries.isArray()) {
                return;
            }
            for (JsonNode entry : entries) {
                for (JsonNode change : entry.path("changes")) {
                    String phoneNumberId = change.path("value").path("metadata").path("phone_number_id").asText(null);
                    if (phoneNumberId == null) {
                        continue;
                    }
                    Optional<SalonWhatsAppConfig> config = salonWhatsAppService.resolveByPhoneNumberId(phoneNumberId);
                    if (config.isPresent()) {
                        TenantContext.setSalonId(config.get().getSalonId());
                        log.info("WhatsApp webhook routed to salonId={} phoneNumberId={}",
                                config.get().getSalonId(), phoneNumberId);
                    } else {
                        log.warn("WhatsApp webhook: unknown phone_number_id={}", phoneNumberId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("WhatsApp webhook routing failed: {}", e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private boolean matchesVerifyToken(String token) {
        if (properties.getWebhookVerifyToken() != null
                && properties.getWebhookVerifyToken().equals(token)) {
            return true;
        }
        return salonWhatsAppService.getForCurrentSalon()
                .map(SalonWhatsAppConfig::getWebhookVerifyToken)
                .filter(t -> t.equals(token))
                .isPresent();
    }
}
