package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.DashboardResponse;
import com.gserp.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<DashboardResponse>> getToday() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailySummary(LocalDate.now())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailySummary(date)));
    }
}
