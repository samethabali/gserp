package com.gserp.notification.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gserp.model.NotificationLog;
import com.gserp.model.SalonWhatsAppConfig;
import com.gserp.repository.NotificationLogRepository;
import com.gserp.service.SalonWhatsAppService;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppClient {

    private final WhatsAppProperties properties;
    private final SalonWhatsAppService salonWhatsAppService;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private record SendConfig(boolean enabled, String apiUrl, String token, String phoneNumberId) {}

    private SendConfig resolveEffectiveConfig() {
        Optional<SalonWhatsAppConfig> salon = salonWhatsAppService.getForCurrentSalon();
        if (salon.isPresent() && salon.get().isEnabled()
                && hasText(salon.get().getTokenEnc()) && hasText(salon.get().getPhoneNumberId())) {
            return new SendConfig(true, properties.getApiUrl(), salon.get().getTokenEnc(), salon.get().getPhoneNumberId());
        }
        if (properties.isEnabled() && hasText(properties.getToken()) && hasText(properties.getPhoneNumberId())) {
            return new SendConfig(true, properties.getApiUrl(), properties.getToken(), properties.getPhoneNumberId());
        }
        return new SendConfig(false, properties.getApiUrl(), "", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public void sendTemplate(Long appointmentId, String toE164, String templateName, List<String> bodyParams) {
        SendConfig config = resolveEffectiveConfig();
        if (!config.enabled()) {
            log.debug("WhatsApp devre dışı, atlanıyor: {}", templateName);
            return;
        }
        if (toE164 == null || toE164.isBlank()) {
            log.warn("WhatsApp alıcısı boş, appointment #{}", appointmentId);
            logAttempt(appointmentId, templateName, toE164, "SKIPPED", "Boş telefon");
            return;
        }
        try {
            String to = toE164.replace("+", "");
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "template",
                    "template", Map.of(
                            "name", templateName,
                            "language", Map.of("code", "tr"),
                            "components", List.of(Map.of(
                                    "type", "body",
                                    "parameters", bodyParams.stream()
                                            .map(p -> Map.of("type", "text", "text", p))
                                            .toList()
                            ))
                    )
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiUrl() + "/" + config.phoneNumberId() + "/messages"))
                    .header("Authorization", "Bearer " + config.token())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logAttempt(appointmentId, templateName, toE164, "SENT", null);
            } else {
                log.warn("WhatsApp API hata {}: {}", response.statusCode(), response.body());
                logAttempt(appointmentId, templateName, toE164, "FAILED", response.body());
            }
        } catch (Exception e) {
            log.error("WhatsApp gönderim hatası", e);
            logAttempt(appointmentId, templateName, toE164, "FAILED", e.getMessage());
        }
    }

    private void logAttempt(Long appointmentId, String template, String recipient, String status, String error) {
        Long salonId = TenantContext.getSalonId();
        if (salonId == null) {
            log.warn("WhatsApp log salon_id eksik, appointment #{}", appointmentId);
            return;
        }
        notificationLogRepository.save(NotificationLog.builder()
                .salonId(salonId)
                .appointmentId(appointmentId)
                .channel("WHATSAPP")
                .templateName(template)
                .recipient(recipient)
                .status(status)
                .errorMessage(error)
                .sentAt(LocalDateTime.now())
                .build());
    }
}
