package com.gserp.service;

import com.gserp.model.Appointment;
import com.gserp.model.WorkingHours;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.WorkingHoursRepository;
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
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end) {
        DayOfWeek dow = start.getDayOfWeek();
        List<WorkingHours> hours = workingHoursRepository.findByStaffId(staffId);

        // Kayıt hiç tanımlanmamışsa kısıtlama uygulanmaz
        if (hours.isEmpty()) return true;

        return hours.stream()
                .filter(wh -> wh.getDayOfWeek() == dow && !wh.isDayOff())
                .anyMatch(wh -> {
                    LocalTime s = start.toLocalTime();
                    return !s.isBefore(wh.getStartTime()) && s.isBefore(wh.getEndTime());
                });
    }

    /**
     * Check if a staff member has no conflicting appointments in the given time range.
     * Excludes the given appointmentId (for move operations).
     */
    public boolean isStaffAvailable(Long staffId, LocalDateTime start, LocalDateTime end, Long excludeAppointmentId) {
        List<Appointment> conflicts = appointmentRepository.findStaffOverlap(
                staffId, start, end, AppointmentStatus.CANCELLED);
        if (excludeAppointmentId != null) {
            conflicts = conflicts.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }
        return conflicts.isEmpty();
    }
}
