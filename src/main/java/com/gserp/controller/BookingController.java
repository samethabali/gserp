package com.gserp.controller;

import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.model.ServiceDefinition;
import com.gserp.model.Staff;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
import com.gserp.notification.whatsapp.WhatsAppProperties;
import com.gserp.service.AppointmentService;
import com.gserp.service.ConsentService;
import com.gserp.service.SalonWhatsAppService;
import com.gserp.service.SchedulerService;
import com.gserp.tenant.TenantContext;
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
    private final WhatsAppProperties whatsAppProperties;
    private final SalonWhatsAppService salonWhatsAppService;
    private final ConsentService consentService;

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> info() {
        Map<String, Object> data = new java.util.HashMap<>();
        boolean waEnabled = salonWhatsAppService.isEnabledForCurrentSalon() || whatsAppProperties.isEnabled();
        data.put("whatsappEnabled", waEnabled);
        String salonPhone = salonWhatsAppService.salonPhoneForCurrentSalon();
        if (salonPhone == null || salonPhone.isBlank()) {
            salonPhone = whatsAppProperties.getSalonPhoneE164();
        }
        if (salonPhone != null && !salonPhone.isBlank()) {
            data.put("salonPhone", salonPhone);
        }
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getServices() {
        Long salonId = TenantContext.requireSalonId();
        return ResponseEntity.ok(ApiResponse.ok(serviceRepository.findBySalonIdAndActiveTrue(salonId)));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<Staff>>> getStaff() {
        Long salonId = TenantContext.requireSalonId();
        return ResponseEntity.ok(ApiResponse.ok(staffRepository.findBySalonIdAndActiveTrue(salonId)));
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
