package com.gscrm.service;

import com.gscrm.model.Coupon;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.DiscountType;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.repository.CouponRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampaignOrgScopeIT {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SalonRepository salonRepository;

    @Autowired
    private CouponRepository couponRepository;

    private Salon salonA;
    private Salon salonB;
    private Salon otherOrgSalon;

    @BeforeEach
    void seedCoupons() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Franchise Org")
                .type(OrganizationType.FRANCHISE)
                .active(true)
                .loyaltyPolicy("ORG")
                .createdAt(now)
                .build());
        Organization otherOrg = organizationRepository.save(Organization.builder()
                .name("Other Org")
                .type(OrganizationType.STANDALONE)
                .active(true)
                .loyaltyPolicy("SALON")
                .createdAt(now)
                .build());

        salonA = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug("scope-a")
                .name("Şube A")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());
        salonB = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug("scope-b")
                .name("Şube B")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());
        otherOrgSalon = salonRepository.save(Salon.builder()
                .organizationId(otherOrg.getId())
                .slug("scope-other")
                .name("Başka Salon")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());

        couponRepository.save(Coupon.builder()
                .salonId(salonA.getId())
                .organizationId(org.getId())
                .scope("ORG")
                .code("FRANCHISE10")
                .description("Org geneli %10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(true)
                .usedCount(0)
                .createdAt(now)
                .build());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void orgCouponValidAcrossSiblingBranches() {
        TenantContext.setSalonId(salonA.getId());
        TenantContext.setOrgId(salonA.getOrganizationId());
        assertDoesNotThrow(() -> campaignService.validateCoupon("FRANCHISE10", "05551234567", null));

        TenantContext.setSalonId(salonB.getId());
        TenantContext.setOrgId(salonB.getOrganizationId());
        assertDoesNotThrow(() -> campaignService.validateCoupon("FRANCHISE10", "05551234567", null));
    }

    @Test
    void orgCouponRejectedForDifferentOrganization() {
        TenantContext.setSalonId(otherOrgSalon.getId());
        TenantContext.setOrgId(otherOrgSalon.getOrganizationId());
        assertThrows(IllegalArgumentException.class,
                () -> campaignService.validateCoupon("FRANCHISE10", "05551234567", null));
    }
}
