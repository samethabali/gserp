package com.gserp.service;

import com.gserp.model.Appointment;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
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
public class SessionReminderService {

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final NotificationService notificationService;

    /**
     * Her gün saat 09:00'da çalışır.
     * Ertesi gün için planlanmış seans randevularını kontrol eder
     * ve bağlı tüm istemcilere bildirim gönderir.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void sendDailySessionReminders() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd   = tomorrowStart.plusDays(1);

        List<Appointment> upcoming = appointmentRepository
                .findUpcomingSessionAppointments(tomorrowStart, tomorrowEnd);

        if (upcoming.isEmpty()) return;

        log.info("Seans hatırlatıcısı: {} randevu bulundu", upcoming.size());

        for (Appointment a : upcoming) {
            if (a.getSessionNumber() == null || a.getTotalSessions() == null) continue;

            String staffName   = staffRepository.findById(a.getStaffId())
                    .map(s -> s.getName()).orElse("Uzman");
            String serviceName = serviceRepository.findById(a.getServiceId())
                    .map(s -> s.getName()).orElse("Hizmet");

            String message = String.format(
                    "📅 Yarın: %s — %s seans %d/%d (%s)",
                    a.getCustomerName(),
                    serviceName,
                    a.getSessionNumber(),
                    a.getTotalSessions(),
                    staffName
            );

            notificationService.broadcastNotification(
                    "SESSION_REMINDER",
                    message,
                    Map.of(
                            "appointmentId", a.getId(),
                            "customerName",  a.getCustomerName(),
                            "serviceName",   serviceName,
                            "staffName",     staffName,
                            "sessionNumber", a.getSessionNumber(),
                            "totalSessions", a.getTotalSessions(),
                            "startTime",     a.getStartTime().toString()
                    )
            );
        }

        log.info("Seans hatırlatıcısı: {} bildirim gönderildi", upcoming.size());
    }

    /**
     * Dashboard için bugünkü aktif seans gruplarının ilerlemesini döner.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveSessionProgress(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        List<Appointment> todaysSessionAppts = appointmentRepository
                .findUpcomingSessionAppointments(start, end);

        return todaysSessionAppts.stream()
                .filter(a -> a.getSessionNumber() != null && a.getTotalSessions() != null)
                .map(a -> {
                    String serviceName = serviceRepository.findById(a.getServiceId())
                            .map(s -> s.getName()).orElse("-");
                    return Map.<String, Object>of(
                            "appointmentId", a.getId(),
                            "customerName",  a.getCustomerName() != null ? a.getCustomerName() : "-",
                            "serviceName",   serviceName,
                            "sessionNumber", a.getSessionNumber(),
                            "totalSessions", a.getTotalSessions(),
                            "startTime",     a.getStartTime().toString()
                    );
                })
                .toList();
    }
}
