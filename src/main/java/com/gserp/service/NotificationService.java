package com.gserp.service;

import com.gserp.dto.response.AppointmentResponse;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast an appointment change to all connected clients for the current salon.
     */
    public void broadcastAppointmentChange(String action, AppointmentResponse appointment) {
        Long salonId = resolveSalonId(appointment);
        Map<String, Object> payload = Map.of(
                "action", action,
                "appointment", appointment
        );
        messagingTemplate.convertAndSend("/topic/salon." + salonId + ".appointments", payload);
        log.debug("WS broadcast: {} appointment #{} salon {}", action, appointment.getId(), salonId);
    }

    /**
     * Broadcast dashboard refresh signal for the current salon.
     */
    public void broadcastDashboardRefresh() {
        Long salonId = TenantContext.requireSalonId();
        messagingTemplate.convertAndSend("/topic/salon." + salonId + ".dashboard", Map.of("action", "REFRESH"));
    }

    /**
     * Broadcast a general notification (session reminders, low stock, etc.)
     */
    public void broadcastNotification(String type, String message, Object data) {
        Long salonId = TenantContext.requireSalonId();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        if (data != null) payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/salon." + salonId + ".notifications", payload);
        log.debug("WS notification: {} — {} salon {}", type, message, salonId);
    }

    public void broadcastNotificationForSalon(Long salonId, String type, String message, Object data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        if (data != null) payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/salon." + salonId + ".notifications", payload);
        log.debug("WS notification: {} — {} salon {}", type, message, salonId);
    }

    private Long resolveSalonId(AppointmentResponse appointment) {
        if (TenantContext.getSalonId() != null) {
            return TenantContext.requireSalonId();
        }
        if (appointment.getSalonId() != null) {
            return appointment.getSalonId();
        }
        throw new IllegalStateException("Salon tenant context is not set");
    }
}
