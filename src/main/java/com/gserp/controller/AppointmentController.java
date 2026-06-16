package com.gserp.controller;

import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.request.AppointmentMoveRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.security.BranchScopeService;
import com.gserp.security.StaffScopeService;
import com.gserp.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST','SPECIALIST')")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final StaffScopeService staffScopeService;
    private final BranchScopeService branchScopeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        Long staffFilter = staffScopeService.specialistStaffId();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getByDate(d, staffFilter)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Randevu oluşturuldu", appointmentService.create(request)));
    }

    @PutMapping("/{id}/move")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> move(
            @PathVariable Long id,
            @RequestBody AppointmentMoveRequest request) {
        request.setAppointmentId(id);
        return ResponseEntity.ok(ApiResponse.ok("Randevu taşındı", appointmentService.move(request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AppointmentResponse>> changeStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        var user = staffScopeService.requireAuthenticatedUser();
        var appointment = appointmentService.getEntity(id);
        branchScopeService.assertCanAccessAppointment(appointment, user);
        staffScopeService.assertCanAccessAppointment(id);
        AppointmentStatus status = AppointmentStatus.valueOf(body.get("status"));
        staffScopeService.assertSpecialistStatusChange(status);
        String reason = body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok("Durum güncellendi", appointmentService.changeStatus(id, status, reason)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> update(
            @PathVariable Long id,
            @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Randevu güncellendi", appointmentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        appointmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Randevu silindi", null));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getHistory(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findByCustomerPhone(phone)));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Randevu onaylandı",
                appointmentService.changeStatus(id, AppointmentStatus.SCHEDULED, null)));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Randevu isteği reddedildi",
                appointmentService.changeStatus(id, AppointmentStatus.CANCELLED, reason)));
    }
}
