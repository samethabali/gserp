package com.gscrm.controller;

import com.gscrm.dto.request.PaymentCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.DailyPaymentSummary;
import com.gscrm.dto.response.PaymentResponse;
import com.gscrm.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> collect(
            @Valid @RequestBody PaymentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tahsilat kaydedildi", paymentService.collect(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByDate(date != null ? date : LocalDate.now())));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DailyPaymentSummary>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getSummary(date != null ? date : LocalDate.now())));
    }

    @GetMapping("/customer")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByCustomerPhone(
            @RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByCustomerPhone(phone)));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByAppointment(
            @PathVariable Long appointmentId) {
        return paymentService.getByAppointmentId(appointmentId)
                .map(p -> ResponseEntity.ok(ApiResponse.ok(p)))
                .orElse(ResponseEntity.notFound().build());
    }
}
