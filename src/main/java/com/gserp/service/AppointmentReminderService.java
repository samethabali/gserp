package com.gserp.service;

import com.gserp.dto.response.AppointmentResponse;
import com.gserp.model.Appointment;
import com.gserp.model.Salon;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.notification.whatsapp.WhatsAppNotificationService;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
import com.gserp.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    private final SalonRepository salonRepository;
    private final StaffRepository staffRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final NotificationService notificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final AppointmentService appointmentService;

    public AppointmentReminderService(
            AppointmentRepository appointmentRepository,
            SalonRepository salonRepository,
            StaffRepository staffRepository,
            ServiceDefinitionRepository serviceRepository,
            NotificationService notificationService,
            WhatsAppNotificationService whatsAppNotificationService,
            @Lazy AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.salonRepository = salonRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.notificationService = notificationService;
        this.whatsAppNotificationService = whatsAppNotificationService;
        this.appointmentService = appointmentService;
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void sendDailyReminders() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd = tomorrowStart.plusDays(1);

        for (Salon salon : salonRepository.findAll()) {
            if (!salon.isActive()) {
                continue;
            }
            Long salonId = salon.getId();
            List<Appointment> upcoming = appointmentRepository.findBySalonIdAndStartTimeBetweenAndStatusIn(
                    salonId, tomorrowStart, tomorrowEnd,
                    List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.IN_PROGRESS));

            for (Appointment a : upcoming) {
                TenantContext.setSalonId(salonId);
                try {
                    AppointmentResponse response = appointmentService.toResponse(a);
                    whatsAppNotificationService.onReminder(response);

                    String staffName = staffRepository.findByIdAndSalonId(a.getStaffId(), salonId)
                            .map(s -> s.getName()).orElse("Uzman");
                    String serviceName = serviceRepository.findByIdAndSalonId(a.getServiceId(), salonId)
                            .map(s -> s.getName()).orElse("Hizmet");
                    String message = String.format("📅 Yarın: %s — %s (%s)", a.getCustomerName(), serviceName, staffName);
                    notificationService.broadcastNotificationForSalon(salonId, "APPOINTMENT_REMINDER", message,
                            Map.of("appointmentId", a.getId()));
                } finally {
                    TenantContext.clear();
                }
            }

            if (!upcoming.isEmpty()) {
                log.info("Hatırlatma salon {}: {} randevu işlendi", salonId, upcoming.size());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessionProgress(LocalDate date) {
        Long salonId = TenantContext.requireSalonId();
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<Appointment> todaysSessionAppts = appointmentRepository
                .findUpcomingSessionAppointments(salonId, start, end);

        return todaysSessionAppts.stream()
                .filter(a -> a.getSessionNumber() != null && a.getTotalSessions() != null)
                .map(a -> {
                    String serviceName = serviceRepository.findByIdAndSalonId(a.getServiceId(), salonId)
                            .map(s -> s.getName()).orElse("-");
                    return Map.<String, Object>of(
                            "appointmentId", a.getId(),
                            "customerName", a.getCustomerName() != null ? a.getCustomerName() : "-",
                            "serviceName", serviceName,
                            "sessionNumber", a.getSessionNumber(),
                            "totalSessions", a.getTotalSessions(),
                            "startTime", a.getStartTime().toString()
                    );
                })
                .toList();
    }
}
