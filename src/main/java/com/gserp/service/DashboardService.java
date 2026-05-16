package com.gserp.service;

import com.gserp.dto.response.DashboardResponse;
import com.gserp.model.Appointment;
import com.gserp.model.Staff;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;

    public DashboardResponse getDailySummary(LocalDate date) {
        List<Appointment> appointments = appointmentRepository.findByStartTimeBetween(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        int total = appointments.size();
        int completed = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        int inProgress = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS).count();
        int scheduled = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED).count();
        int noShow = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        int cancelled = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();

        BigDecimal totalRevenue = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .map(Appointment::getFinalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedRevenue = appointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .map(Appointment::getFinalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Staff performance
        Map<Long, List<Appointment>> byStaff = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getStaffId));

        List<DashboardResponse.StaffPerformance> staffPerf = new ArrayList<>();
        for (var entry : byStaff.entrySet()) {
            Staff staff = staffRepository.findById(entry.getKey()).orElse(null);
            if (staff == null) continue;

            List<Appointment> staffAppts = entry.getValue();
            int sCompleted = (int) staffAppts.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            int sNoShows = (int) staffAppts.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
            BigDecimal sRevenue = staffAppts.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .map(Appointment::getFinalPrice)
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            staffPerf.add(DashboardResponse.StaffPerformance.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .staffColor(staff.getColorHex())
                    .totalAppointments(staffAppts.size())
                    .completed(sCompleted)
                    .noShows(sNoShows)
                    .revenue(sRevenue)
                    .build());
        }

        return DashboardResponse.builder()
                .totalAppointments(total)
                .completedAppointments(completed)
                .inProgressAppointments(inProgress)
                .scheduledAppointments(scheduled)
                .cancelledAppointments(cancelled)
                .noShows(noShow)
                .totalRevenue(totalRevenue)
                .expectedRevenue(expectedRevenue)
                .staffPerformance(staffPerf)
                .build();
    }
}
