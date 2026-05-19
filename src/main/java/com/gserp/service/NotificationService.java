package com.gserp.service;

import com.gserp.dto.response.AppointmentResponse;
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
     * Broadcast an appointment change to all connected clients.
     */
    public void broadcastAppointmentChange(String action, AppointmentResponse appointment) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "appointment", appointment
        );
        messagingTemplate.convertAndSend("/topic/appointments", payload);
        log.debug("WS broadcast: {} appointment #{}", action, appointment.getId());
    }

    /**
     * Broadcast dashboard refresh signal.
     */
    public void broadcastDashboardRefresh() {
        messagingTemplate.convertAndSend("/topic/dashboard", Map.of("action", "REFRESH"));
    }

    /**
     * Broadcast a general notification (session reminders, low stock, etc.)
     */
    public void broadcastNotification(String type, String message, Object data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        if (data != null) payload.put("data", data);
        messagingTemplate.convertAndSend("/topic/notifications", payload);
        log.debug("WS notification: {} — {}", type, message);
    }
}
