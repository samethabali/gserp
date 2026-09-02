package com.gscrm.controller;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.PublicStaffResponse;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.service.AppointmentService;
import com.gscrm.service.ConsentService;
import com.gscrm.service.SchedulerService;
import com.gscrm.tenant.TenantContext;
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
    private final ConsentService consentService;

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getServices() {
        Long salonId = TenantContext.requireSalonId();
        return ResponseEntity.ok(ApiResponse.ok(serviceRepository.findBySalonIdAndActiveTrue(salonId)));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<PublicStaffResponse>>> getStaff() {
        Long salonId = TenantContext.requireSalonId();
        List<PublicStaffResponse> staff = staffRepository.findBySalonIdAndActiveTrue(salonId).stream()
                .map(PublicStaffResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(staff));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAvailability(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long salonId = TenantContext.requireSalonId();
        ServiceDefinition service = serviceRepository.findByIdAndSalonId(serviceId, salonId).orElse(null);
        if (service == null) return ResponseEntity.ok(ApiResponse.ok(List.of()));

        int duration = service.getDurationMinutes();
        List<Map<String, Object>> slots = new ArrayList<>();

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
        if (request.getConsentTypes() != null && !request.getConsentTypes().isEmpty()) {
            consentService.findOrCreateCustomerForBooking(
                    request.getCustomerName(), request.getCustomerPhone(), request.getConsentTypes());
        }
        AppointmentResponse response = appointmentService.createRequest(request);
        return ResponseEntity.ok(ApiResponse.ok("Randevu isteğiniz alındı, salon onayı bekleniyor", response));
    }
}
