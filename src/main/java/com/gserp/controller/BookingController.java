package com.gserp.controller;

import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.model.ServiceDefinition;
import com.gserp.model.Staff;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
import com.gserp.service.AppointmentService;
import com.gserp.service.SchedulerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final ServiceDefinitionRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final AppointmentService appointmentService;
    private final SchedulerService schedulerService;

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getServices() {
        return ResponseEntity.ok(ApiResponse.ok(serviceRepository.findByActiveTrue()));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<Staff>>> getStaff() {
        return ResponseEntity.ok(ApiResponse.ok(staffRepository.findAll().stream()
                .filter(Staff::isActive)
                .toList()));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAvailability(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ServiceDefinition service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) return ResponseEntity.ok(ApiResponse.ok(List.of()));

        int duration = service.getDurationMinutes();
        List<Map<String, Object>> slots = new ArrayList<>();

        // 08:00 - 20:00 arası 30 dakikalık dilimler
        LocalTime cursor = LocalTime.of(8, 0);
        LocalTime limit  = LocalTime.of(20, 0);

        while (!cursor.isAfter(limit.minusMinutes(duration))) {
            LocalDateTime start = date.atTime(cursor);
            LocalDateTime end   = start.plusMinutes(duration);

            boolean withinHours = schedulerService.isWithinWorkingHours(staffId, start, end);
            boolean available   = withinHours && schedulerService.isStaffAvailable(staffId, start, end, null);

            slots.add(Map.of(
                    "time",      cursor.toString(),
                    "available", available
            ));
            cursor = cursor.plusMinutes(30);
        }

        return ResponseEntity.ok(ApiResponse.ok(slots));
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody AppointmentCreateRequest request) {
        AppointmentResponse response = appointmentService.create(request);
        return ResponseEntity.ok(ApiResponse.ok("Randevunuz başarıyla oluşturuldu", response));
    }
}
