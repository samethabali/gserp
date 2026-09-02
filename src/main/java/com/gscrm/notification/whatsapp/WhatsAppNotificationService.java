package com.gscrm.notification.whatsapp;

import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.service.SalonWhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WhatsAppNotificationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final WhatsAppClient whatsAppClient;
    private final WhatsAppProperties properties;
    private final SalonWhatsAppService salonWhatsAppService;

    public void onRequestReceived(AppointmentResponse appointment) {
        whatsAppClient.sendTemplate(
                appointment.getId(),
                normalizePhone(appointment.getCustomerPhone()),
                "appointment_request_received",
                List.of(
                        appointment.getCustomerName() != null ? appointment.getCustomerName() : "Müşteri",
                        appointment.getServiceName(),
                        appointment.getStartTime().format(FMT)
                ));
    }

    public void onApproved(AppointmentResponse appointment) {
        whatsAppClient.sendTemplate(
                appointment.getId(),
                normalizePhone(appointment.getCustomerPhone()),
                "appointment_confirmed",
                List.of(
                        appointment.getCustomerName() != null ? appointment.getCustomerName() : "Müşteri",
                        appointment.getServiceName(),
                        appointment.getStaffName(),
                        appointment.getStartTime().format(FMT)
                ));
    }

    public void onCancelled(AppointmentResponse appointment, String reason) {
        whatsAppClient.sendTemplate(
                appointment.getId(),
                normalizePhone(appointment.getCustomerPhone()),
                "appointment_cancelled",
                List.of(
                        appointment.getCustomerName() != null ? appointment.getCustomerName() : "Müşteri",
                        appointment.getStartTime().format(FMT),
                        reason != null ? reason : "Salon tarafından iptal"
                ));
    }

    public void onReminder(AppointmentResponse appointment) {
        whatsAppClient.sendTemplate(
                appointment.getId(),
                normalizePhone(appointment.getCustomerPhone()),
                "appointment_reminder",
                List.of(
                        appointment.getCustomerName() != null ? appointment.getCustomerName() : "Müşteri",
                        appointment.getServiceName(),
                        appointment.getStartTime().format(FMT)
                ));
    }

    public String waMeLink(String message) {
        String salonPhone = salonWhatsAppService.salonPhoneForCurrentSalon();
        if (salonPhone == null || salonPhone.isBlank()) {
            salonPhone = properties.getSalonPhoneE164();
        }
        if (salonPhone == null || salonPhone.isBlank()) {
            return null;
        }
        String phone = salonPhone.replace("+", "");
        String encoded = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        return "https://wa.me/" + phone + "?text=" + encoded;
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("05") && digits.length() == 11) {
            return "+90" + digits.substring(1);
        }
        if (digits.startsWith("5") && digits.length() == 10) {
            return "+90" + digits;
        }
        if (digits.startsWith("90") && digits.length() == 12) {
            return "+" + digits;
        }
        return null;
    }
}
