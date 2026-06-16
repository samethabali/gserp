package com.gserp.notification.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gserp.model.NotificationLog;
import com.gserp.repository.NotificationLogRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppClient {

    private final WhatsAppProperties properties;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendTemplate(Long appointmentId, String toE164, String templateName, List<String> bodyParams) {
        if (!properties.isEnabled()) {
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
                    .uri(URI.create(properties.getApiUrl() + "/" + properties.getPhoneNumberId() + "/messages"))
                    .header("Authorization", "Bearer " + properties.getToken())
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
        notificationLogRepository.save(NotificationLog.builder()
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
