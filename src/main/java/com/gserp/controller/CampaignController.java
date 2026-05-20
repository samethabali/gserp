package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.Coupon;
import com.gserp.model.LoyaltyTier;
import com.gserp.model.enums.DiscountType;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.UserRepository;
import com.gserp.security.AuthenticatedUser;
import com.gserp.service.CampaignService;
import com.gserp.service.CampaignService.CouponValidationResult;
import com.gserp.service.CampaignService.LoyaltyInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    // ─── Request Records ───

    public record CouponCreateRequest(
            @NotBlank String code,
            String description,
            @NotNull DiscountType discountType,
            @NotNull @Positive BigDecimal discountValue,
            int minAppointments,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            Integer maxUses
    ) {}

    public record LoyaltyTierRequest(
            @NotBlank String name,
            int minCompleted,
            @NotNull @Positive BigDecimal discountPercentage
    ) {}

    // ─── Admin: Kupon Yönetimi ───

    @GetMapping("/coupons")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<List<Coupon>>> listCoupons() {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.getAllCoupons()));
    }

    @PostMapping("/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(@Valid @RequestBody CouponCreateRequest req) {
        Coupon coupon = Coupon.builder()
                .code(req.code())
                .description(req.description())
                .discountType(req.discountType())
                .discountValue(req.discountValue())
                .minAppointments(req.minAppointments())
                .validFrom(req.validFrom())
                .validUntil(req.validUntil())
                .maxUses(req.maxUses())
                .active(true)
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Kupon oluşturuldu", campaignService.createCoupon(coupon)));
    }

    @PutMapping("/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> updateCoupon(
            @PathVariable Long id,
            @RequestBody CouponCreateRequest req) {
        Coupon patch = Coupon.builder()
                .description(req.description())
                .discountType(req.discountType())
                .discountValue(req.discountValue())
                .validFrom(req.validFrom())
                .validUntil(req.validUntil())
                .maxUses(req.maxUses())
                .active(true)
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Kupon güncellendi", campaignService.updateCoupon(id, patch)));
    }

    @PatchMapping("/coupons/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(@PathVariable Long id) {
        Coupon patch = Coupon.builder().active(false).build();
        campaignService.updateCoupon(id, patch);
        return ResponseEntity.ok(ApiResponse.ok("Kupon deaktif edildi", null));
    }

    // ─── Admin: Sadakat Eşikleri ───

    @GetMapping("/loyalty-tiers")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<List<LoyaltyTier>>> listTiers() {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.getAllTiers()));
    }

    @PostMapping("/loyalty-tiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoyaltyTier>> createTier(@Valid @RequestBody LoyaltyTierRequest req) {
        LoyaltyTier tier = LoyaltyTier.builder()
                .name(req.name())
                .minCompleted(req.minCompleted())
                .discountPercentage(req.discountPercentage())
                .active(true)
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Sadakat eşiği oluşturuldu", campaignService.saveTier(tier)));
    }

    @DeleteMapping("/loyalty-tiers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTier(@PathVariable Long id) {
        campaignService.deleteTier(id);
        return ResponseEntity.ok(ApiResponse.ok("Sadakat eşiği silindi", null));
    }

    // ─── Public / Customer: Kupon Doğrulama ───

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCoupon(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Kupon kodu boş olamaz"));
        }

        String customerPhone = resolvePhone(principal);
        Long customerId = resolveCustomerId(principal);

        try {
            CouponValidationResult result = campaignService.validateCoupon(code, customerPhone, customerId);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "couponId",      result.couponId(),
                    "code",          result.code(),
                    "discountType",  result.discountType().name(),
                    "discountValue", result.discountValue(),
                    "description",   result.description() != null ? result.description() : ""
            )));
        } catch (IllegalArgumentException | com.gserp.exception.ConflictException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Customer: Sadakat Bilgisi ───

    @GetMapping("/loyalty-info")
    public ResponseEntity<ApiResponse<LoyaltyInfo>> loyaltyInfo(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String phone = resolvePhone(principal);
        return ResponseEntity.ok(ApiResponse.ok(campaignService.getLoyaltyInfo(phone)));
    }

    // ─── Helpers ───

    private String resolvePhone(AuthenticatedUser principal) {
        if (principal == null) return null;
        return userRepository.findById(principal.getId())
                .flatMap(u -> u.getCustomerId() != null
                        ? customerRepository.findById(u.getCustomerId()) : java.util.Optional.empty())
                .map(c -> c.getPhone())
                .orElse(null);
    }

    private Long resolveCustomerId(AuthenticatedUser principal) {
        if (principal == null) return null;
        return userRepository.findById(principal.getId())
                .map(u -> u.getCustomerId())
                .orElse(null);
    }
}
