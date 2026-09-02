package com.gscrm.service;

import com.gscrm.model.Appointment;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    private final SalonRepository salonRepository;
    private final StaffRepository staffRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final NotificationService notificationService;

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
                TenantContext.setOrgId(salon.getOrganizationId());
                try {
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
