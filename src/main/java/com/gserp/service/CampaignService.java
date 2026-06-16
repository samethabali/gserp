package com.gserp.service;

import com.gserp.exception.ConflictException;
import com.gserp.model.Coupon;
import com.gserp.model.CouponUsage;
import com.gserp.model.LoyaltyTier;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.model.enums.CouponScope;
import com.gserp.model.enums.DiscountType;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.CouponRepository;
import com.gserp.repository.CouponUsageRepository;
import com.gserp.repository.LoyaltyTierRepository;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final LoyaltyTierRepository loyaltyTierRepository;
    private final AppointmentRepository appointmentRepository;

    public record CouponValidationResult(
            Long couponId,
            String code,
            DiscountType discountType,
            BigDecimal discountValue,
            String description
    ) {}

    public record LoyaltyInfo(
            String tierName,
            int completedCount,
            BigDecimal discountPercentage,
            int nextTierAt,
            String nextTierName
    ) {}

    /**
     * Kupon kodunu doğrular; geçerliyse indirim bilgisini döner, değilse hata fırlatır.
     */
    public CouponValidationResult validateCoupon(String code, String customerPhone, Long customerId) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz kupon kodu"));

        assertCouponScope(coupon);

        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Bu kupon artık aktif değil");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new IllegalArgumentException("Bu kuponun geçerlilik tarihi henüz başlamadı");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            throw new IllegalArgumentException("Bu kuponun süresi dolmuş");
        }
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            throw new IllegalArgumentException("Bu kupon maksimum kullanım sayısına ulaştı");
        }

        if (coupon.getMinAppointments() > 0 && customerPhone != null) {
            long completed = appointmentRepository.countBySalonIdAndCustomerPhoneAndStatus(
                    TenantContext.requireSalonId(), customerPhone, AppointmentStatus.COMPLETED);
            if (completed < coupon.getMinAppointments()) {
                throw new ConflictException("Bu kuponu kullanmak için en az "
                        + coupon.getMinAppointments() + " tamamlanan randevunuz olmalı");
            }
        }

        return new CouponValidationResult(
                coupon.getId(), coupon.getCode(),
                coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getDescription()
        );
    }

    /**
     * Verilen base price'a kupon indirimini uygular.
     */
    public BigDecimal applyCouponDiscount(BigDecimal basePrice, CouponValidationResult coupon) {
        if (coupon.discountType() == DiscountType.PERCENTAGE) {
            BigDecimal discount = basePrice.multiply(coupon.discountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return basePrice.subtract(discount).max(BigDecimal.ZERO);
        } else {
            return basePrice.subtract(coupon.discountValue()).max(BigDecimal.ZERO);
        }
    }

    /**
     * Müşterinin telefon numarasına göre sadakat tierını bulur.
     */
    public Optional<LoyaltyTier> getApplicableTier(String customerPhone) {
        if (customerPhone == null || customerPhone.isBlank()) return Optional.empty();
        long completed = appointmentRepository.countBySalonIdAndCustomerPhoneAndStatus(
                TenantContext.requireSalonId(), customerPhone, AppointmentStatus.COMPLETED);
        return loyaltyTierRepository.findByActiveTrueOrderByMinCompletedDesc()
                .stream()
                .filter(t -> completed >= t.getMinCompleted())
                .findFirst();
    }

    /**
     * Müşterinin tam sadakat bilgisini döner (portal için).
     */
    public LoyaltyInfo getLoyaltyInfo(String customerPhone) {
        long completed = customerPhone != null
                ? appointmentRepository.countBySalonIdAndCustomerPhoneAndStatus(
                        TenantContext.requireSalonId(), customerPhone, AppointmentStatus.COMPLETED)
                : 0;

        List<LoyaltyTier> tiers = loyaltyTierRepository.findByActiveTrueOrderByMinCompletedDesc();

        LoyaltyTier current = tiers.stream()
                .filter(t -> completed >= t.getMinCompleted())
                .findFirst()
                .orElse(null);

        LoyaltyTier next = tiers.stream()
                .filter(t -> completed < t.getMinCompleted())
                .reduce((a, b) -> a.getMinCompleted() < b.getMinCompleted() ? a : b)
                .orElse(null);

        return new LoyaltyInfo(
                current != null ? current.getName() : null,
                (int) completed,
                current != null ? current.getDiscountPercentage() : BigDecimal.ZERO,
                next != null ? next.getMinCompleted() : -1,
                next != null ? next.getName() : null
        );
    }

    @Transactional
    public void recordUsage(Long couponId, Long customerId, Long appointmentId) {
        couponRepository.findById(couponId).ifPresent(c -> {
            c.setUsedCount(c.getUsedCount() + 1);
            couponRepository.save(c);
        });
        couponUsageRepository.save(CouponUsage.builder()
                .couponId(couponId)
                .customerId(customerId)
                .appointmentId(appointmentId)
                .usedAt(LocalDateTime.now())
                .build());
    }

    // ─── Admin CRUD ───

    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        if (couponRepository.existsByCodeIgnoreCase(coupon.getCode())) {
            throw new IllegalArgumentException("Bu kupon kodu zaten mevcut");
        }
        coupon.setCode(coupon.getCode().toUpperCase());
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUsedCount(0);
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon updateCoupon(Long id, Coupon patch) {
        Coupon existing = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kupon bulunamadı"));
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getDiscountType() != null) existing.setDiscountType(patch.getDiscountType());
        if (patch.getDiscountValue() != null) existing.setDiscountValue(patch.getDiscountValue());
        if (patch.getValidFrom() != null) existing.setValidFrom(patch.getValidFrom());
        if (patch.getValidUntil() != null) existing.setValidUntil(patch.getValidUntil());
        if (patch.getMaxUses() != null) existing.setMaxUses(patch.getMaxUses());
        existing.setActive(patch.isActive());
        return couponRepository.save(existing);
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @Transactional
    public LoyaltyTier saveTier(LoyaltyTier tier) {
        return loyaltyTierRepository.save(tier);
    }

    @Transactional
    public void deleteTier(Long id) {
        loyaltyTierRepository.deleteById(id);
    }

    public List<LoyaltyTier> getAllTiers() {
        return loyaltyTierRepository.findByActiveTrueOrderByMinCompletedDesc();
    }

    private void assertCouponScope(Coupon coupon) {
        Long salonId = TenantContext.requireSalonId();
        Long orgId = TenantContext.getOrgId();
        CouponScope scope = CouponScope.valueOf(coupon.getScope() != null ? coupon.getScope() : "SALON");
        switch (scope) {
            case SALON -> {
                if (!salonId.equals(coupon.getSalonId())) {
                    throw new IllegalArgumentException("Bu kupon bu şubede geçerli değil");
                }
            }
            case ORG -> {
                if (orgId == null || coupon.getOrganizationId() == null || !orgId.equals(coupon.getOrganizationId())) {
                    throw new IllegalArgumentException("Bu kupon bu organizasyonda geçerli değil");
                }
            }
            case GLOBAL -> { /* platform-wide */ }
        }
    }
}
