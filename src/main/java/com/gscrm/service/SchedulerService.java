package com.gscrm.service;

import com.gscrm.model.Appointment;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulerService {

    private final WorkingHoursRepository workingHoursRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * Validate that the given time range falls within the staff's working hours.
     *
     * <p><b>Bilinçli olarak toleranslı:</b> bu aşırı yükleme mesai taşmasına izin verir
     * (bkz. 4 argümanlı sürüm). Panel takviminde uzmanlar günün son slotunu düzenli
     * olarak kullanıyor; taşmayı burada yasaklamak mevcut davranışta regresyon olurdu.
     * Online randevu yolu bu metodu <b>kullanmaz</b> — o taraf {@code AvailabilityService}
     * üzerinden gider ve mesai bitişini üreteç düzeyinde zaten uygular.
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end) {
        return isWithinWorkingHours(staffId, start, end, true);
    }

    /**
     * @param allowOverrun {@code false} ise randevunun bitişi de mesai penceresi içinde olmalı.
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end,
                                        boolean allowOverrun) {
        DayOfWeek dow = start.getDayOfWeek();
        List<WorkingHours> hours = workingHoursRepository.findByStaffId(staffId);

        // Kayıt hiç tanımlanmamışsa kısıtlama uygulanmaz.
        // StaffService.create çalışma saati seed etmediği için yeni uzmanda normal durum budur;
        // burayı "hiçbir şey uygun değil"e çevirmek panel takvimini kilitler.
        if (hours.isEmpty()) return true;

        boolean sameDay = end == null || start.toLocalDate().equals(end.toLocalDate());

        return hours.stream()
                .filter(wh -> wh.getDayOfWeek() == dow && !wh.isDayOff())
                .filter(wh -> wh.getStartTime() != null && wh.getEndTime() != null)
                .anyMatch(wh -> {
                    LocalTime s = start.toLocalTime();
                    boolean startOk = !s.isBefore(wh.getStartTime()) && s.isBefore(wh.getEndTime());
                    if (!startOk) return false;
                    if (allowOverrun || end == null) return true;
                    // Ertesi güne sarkan randevu hiçbir pencereye sığmaz.
                    return sameDay && !end.toLocalTime().isAfter(wh.getEndTime());
                });
    }

    /**
     * Check if a staff member has no conflicting appointments in the given time range.
     * Excludes the given appointmentId (for move operations).
     */
    public boolean isStaffAvailable(Long staffId, LocalDateTime start, LocalDateTime end, Long excludeAppointmentId) {
        List<Appointment> conflicts = appointmentRepository.findStaffOverlap(
                TenantContext.requireSalonId(), staffId, start, end, AppointmentStatus.CANCELLED);
        if (excludeAppointmentId != null) {
            conflicts = conflicts.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }
        return conflicts.isEmpty();
    }
}
