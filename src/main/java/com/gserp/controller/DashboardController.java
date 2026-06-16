package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.DailyTrendDto;
import com.gserp.dto.response.DashboardResponse;
import com.gserp.service.DashboardService;
import com.gserp.service.AppointmentReminderService;
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
@PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','SPECIALIST')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AppointmentReminderService appointmentReminderService;

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<DashboardResponse>> getToday() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailySummary(LocalDate.now())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailySummary(date)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessionProgress(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentReminderService.getActiveSessionProgress(date != null ? date : LocalDate.now())));
    }

    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<List<DailyTrendDto>>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getTrend(Math.min(days, 30))));
    }
}
