package com.gscrm.controller;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.Customer;
import com.gscrm.model.LoyaltyTier;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.service.AppointmentService;
import com.gscrm.service.CampaignService;
import com.gscrm.service.CampaignService.CouponValidationResult;
import com.gscrm.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPortalController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final CampaignService campaignService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Customer customer = resolveCustomer(principal);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "id", customer.getId(),
                "firstName", customer.getFirstName(),
                "lastName", customer.getLastName() != null ? customer.getLastName() : "",
                "email", customer.getEmail() != null ? customer.getEmail() : "",
                "phone", customer.getPhone() != null ? customer.getPhone() : "",
                "balance", customer.getBalance()
        )));
    }

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<Map<String, List<AppointmentResponse>>>> appointments(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Customer customer = resolveCustomer(principal);
        String phone = customer.getPhone();
        Long salonId = TenantContext.requireSalonId();

        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.PENDING_APPROVAL,
                AppointmentStatus.SCHEDULED,
                AppointmentStatus.IN_PROGRESS
        );
        List<AppointmentStatus> pastStatuses = List.of(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW
        );

        List<AppointmentResponse> active = phone != null
                ? appointmentRepository.findBySalonIdAndCustomerPhoneAndStatusIn(salonId, phone, activeStatuses)
                        .stream().map(appointmentService::toResponse).toList()
                : List.of();

        List<AppointmentResponse> past = phone != null
                ? appointmentRepository.findBySalonIdAndCustomerPhoneAndStatusIn(salonId, phone, pastStatuses)
                        .stream().map(appointmentService::toResponse).toList()
                : List.of();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("active", active, "past", past)));
    }

    @PostMapping("/appointments/request")
    @Transactional
    public ResponseEntity<ApiResponse<AppointmentResponse>> requestAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AppointmentCreateRequest req) {
        Customer customer = resolveCustomer(principal);
        String phone = customer.getPhone() != null ? customer.getPhone() : "";

        // Kupon doğrulama
        CouponValidationResult coupon = null;
        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            try {
                coupon = campaignService.validateCoupon(req.getCouponCode(), phone, customer.getId());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Kupon hatası: " + e.getMessage()));
            }
        }

        // Sadakat indirimi — kupon yoksa otomatik tier uygula
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;
        String loyaltyNote = null;
        if (coupon == null) {
            Optional<LoyaltyTier> tier = campaignService.getApplicableTier(phone);
            if (tier.isPresent()) {
                loyaltyDiscount = tier.get().getDiscountPercentage();
                loyaltyNote = tier.get().getName() + " sadakat indirimi";
            }
        }

        // finalPrice ve adjustment hesaplanacak; base price AppointmentService'de belirleniyor.
        // Burada adjustment'ı negatif olarak setliyoruz; service bunu fiyata ekleyecek.
        BigDecimal adjustment = null;
        String adjustmentNote = null;

        if (coupon != null) {
            // Kupon — AppointmentService'de basePrice belli olduğunda uygulayacağız.
            // Şimdilik adjustmentNote'a bilgi koyalım; service finalPrice'ı ayarlar.
            adjustmentNote = "Kupon: " + coupon.code();
        } else if (loyaltyDiscount.compareTo(BigDecimal.ZERO) > 0) {
            adjustmentNote = loyaltyNote;
        }

        final CouponValidationResult finalCoupon = coupon;
        final BigDecimal finalLoyaltyDiscount = loyaltyDiscount;

        AppointmentCreateRequest filledReq = AppointmentCreateRequest.builder()
                .customerName(customer.getFirstName() + (customer.getLastName() != null ? " " + customer.getLastName() : ""))
                .customerPhone(phone)
                .staffId(req.getStaffId())
                .serviceId(req.getServiceId())
                .startTime(req.getStartTime())
                .internalNote(req.getInternalNote())
                .adjustmentNote(adjustmentNote)
                .couponCode(req.getCouponCode())
                .build();

        AppointmentResponse response = appointmentService.createRequest(filledReq, finalCoupon, finalLoyaltyDiscount);

        // Kupon kullanımını kaydet
        if (finalCoupon != null) {
            campaignService.recordUsage(finalCoupon.couponId(), customer.getId(), response.getId());
        }

        return ResponseEntity.ok(ApiResponse.ok("Randevu isteğiniz alındı, onay bekleniyor", response));
    }

    @DeleteMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        Customer customer = resolveCustomer(principal);

        var appointment = appointmentRepository.findByIdAndSalonId(id, TenantContext.requireSalonId())
                .orElse(null);

        if (appointment == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Randevu bulunamadı"));
        }

        String phone = customer.getPhone();
        if (phone == null || !phone.equals(appointment.getCustomerPhone())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Bu randevu size ait değil"));
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL
                && appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Bu randevu iptal edilemez"));
        }

        if (appointment.getStatus() == AppointmentStatus.SCHEDULED
                && appointment.getStartTime().isBefore(LocalDateTime.now().plusHours(2))) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Randevu başlangıcına 2 saatten az kaldığında iptal edilemez"));
        }

        appointmentService.changeStatus(id, AppointmentStatus.CANCELLED, "Müşteri tarafından iptal edildi");
        return ResponseEntity.ok(ApiResponse.ok("Randevunuz iptal edildi", null));
    }

    private Customer resolveCustomer(AuthenticatedUser principal) {
        var user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı"));
        return customerRepository.findById(user.getCustomerId())
                .orElseThrow(() -> new IllegalStateException("Müşteri kaydı bulunamadı"));
    }
}
