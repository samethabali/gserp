package com.gserp.service;

import com.gserp.model.*;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final MockDataStore store;

    /**
     * Validate that the given time range falls within the staff's working hours.
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end) {
        DayOfWeek dow = start.getDayOfWeek();
        List<WorkingHours> hours = store.findWorkingHoursByStaff(staffId);

        return hours.stream()
                .filter(wh -> wh.getDayOfWeek() == dow && !wh.isDayOff())
                .anyMatch(wh -> {
                    LocalTime s = start.toLocalTime();
                    LocalTime e = end.toLocalTime();
                    return !s.isBefore(wh.getStartTime()) && !e.isAfter(wh.getEndTime());
                });
    }

    /**
     * Check if a staff member has no conflicting appointments in the given time range.
     * Excludes the given appointmentId (for move operations).
     */
    public boolean isStaffAvailable(Long staffId, LocalDateTime start, LocalDateTime end, Long excludeAppointmentId) {
        List<Appointment> conflicts = store.findAppointmentsByStaffAndDateRange(staffId, start, end);
        if (excludeAppointmentId != null) {
            conflicts = conflicts.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }
        return conflicts.isEmpty();
    }
}
