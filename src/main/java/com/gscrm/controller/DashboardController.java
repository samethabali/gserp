package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.DailyTrendDto;
import com.gscrm.dto.response.DashboardResponse;
import com.gscrm.security.StaffScopeService;
import com.gscrm.service.DashboardService;
import com.gscrm.service.AppointmentReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST','SPECIALIST')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AppointmentReminderService appointmentReminderService;
    /**
     * Uzman (SPECIALIST) hesabı yalnızca kendi randevularını görür; filtre oturumdaki
     * kimlikten çözülür, istemciden parametre olarak alınmaz.
     */
    private final StaffScopeService staffScopeService;

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<DashboardResponse>> getToday() {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.getDailySummary(LocalDate.now(), staffScopeService.specialistStaffId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.getDailySummary(date, staffScopeService.specialistStaffId())));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessionProgress(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentReminderService.getActiveSessionProgress(
                        date != null ? date : LocalDate.now(), staffScopeService.specialistStaffId())));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<DailyTrendDto>>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.getTrend(Math.min(days, 30), staffScopeService.specialistStaffId())));
    }
}
