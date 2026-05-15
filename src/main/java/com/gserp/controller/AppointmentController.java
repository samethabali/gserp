package com.gserp.controller;

import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.request.AppointmentMoveRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getByDate(d)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Randevu oluşturuldu", appointmentService.create(request)));
    }

    @PutMapping("/{id}/move")
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
        AppointmentStatus status = AppointmentStatus.valueOf(body.get("status"));
        String reason = body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok("Durum güncellendi", appointmentService.changeStatus(id, status, reason)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> update(
            @PathVariable Long id,
            @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Randevu güncellendi", appointmentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        appointmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Randevu silindi", null));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getHistory(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findByCustomerPhone(phone)));
    }
}
